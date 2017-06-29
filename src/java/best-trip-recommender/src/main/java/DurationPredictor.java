
import org.apache.spark.api.java.JavaSparkContext;

import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.spark.SparkConf;
import org.apache.spark.ml.Pipeline;
import org.apache.spark.ml.PipelineStage;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.ml.feature.VectorIndexer;
import org.apache.spark.ml.regression.LinearRegression;
import org.apache.spark.ml.regression.LinearRegressionModel;
import org.apache.spark.ml.regression.LinearRegressionTrainingSummary;
import org.apache.spark.mllib.linalg.Vectors;
import org.apache.spark.sql.DataFrame;
import org.apache.spark.sql.SQLContext;

public class DurationPredictor {

	public static void main(String[] args) throws URISyntaxException, IOException {
		SparkConf sconf = new SparkConf().setAppName("Duration Predictor").setMaster("local[*]");
		JavaSparkContext jsc = new JavaSparkContext(sconf);
		SQLContext sqlContext = new SQLContext(jsc);

		// Load training data
		DataFrame training = sqlContext.read()
				.format("com.databricks.spark.csv")
				.option("header", "true")
				.option("inferSchema", "true")
				.load(args[0]);

		training.show(1);

		training.printSchema();
		
		// Automatically identify categorical features, and index them.
		// Set maxCategories so features with > 4 distinct values are treated as
		// continuous.
		String[] categoricalFeatures = {"route", "week_day"};
		PipelineStage[] featuresIndexers = new PipelineStage[categoricalFeatures.length];
		
		for (int i = 0; i < categoricalFeatures.length; i++) {
			featuresIndexers[i] = new VectorIndexer()
					.setInputCol(categoricalFeatures[i])
					.setOutputCol(categoricalFeatures[i] + "_index")
					// Number of routes
					.setMaxCategories(240);
		}
		
		Pipeline pipeline = new Pipeline()
				  .setStages(featuresIndexers);
		
		DataFrame trainingDF = pipeline
				.fit(training)
				.transform(training);
		
		trainingDF.show(1);

		trainingDF.printSchema();

		VectorAssembler assembler = new VectorAssembler()
				.setInputCols(new String[] {"departure", "arrival", "route", "week_day",
						"difference_previous_schedule", "difference_next_schedule"})
				.setOutputCol("features");

		trainingDF = assembler.transform(training);

		trainingDF.show(1);

		trainingDF.printSchema();

		LinearRegression lr = new LinearRegression()
				.setMaxIter(10)
				.setRegParam(0.3)
				.setElasticNetParam(1.0)
				.setLabelCol("duration")
				.setFeaturesCol("features");
		
		// Fit the model
		LinearRegressionModel lrModel = lr.fit(trainingDF);

		// Print the coefficients and intercept for linear regression
		System.out.println("Coefficients: " + lrModel.coefficients() + " Intercept: " + lrModel.intercept());

		// Summarize the model over the training set and print out some metrics
		LinearRegressionTrainingSummary trainingSummary = lrModel.summary();
		System.out.println("numIterations: " + trainingSummary.totalIterations());
		System.out.println("objectiveHistory: " + Vectors.dense(trainingSummary.objectiveHistory()));
		trainingSummary.residuals().show();
		System.out.println("RMSE: " + trainingSummary.rootMeanSquaredError());
		System.out.println("r2: " + trainingSummary.r2());

		jsc.stop();

	}
}
