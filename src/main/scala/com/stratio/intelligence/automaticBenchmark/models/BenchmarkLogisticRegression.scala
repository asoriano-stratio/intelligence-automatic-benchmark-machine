package com.stratio.intelligence.automaticBenchmark.models

import org.apache.spark.SparkContext
import org.apache.spark.mllib.classification.{LogisticRegressionModel, LogisticRegressionWithLBFGS}
import org.apache.spark.mllib.evaluation.{BinaryClassificationMetrics, MulticlassMetrics}
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.rdd.RDD

class BenchmarkLogisticRegression( sc:SparkContext, numClasses: Int) extends MLModel{

   def train(miscelaneaMap: scala.collection.mutable.HashMap[String,Object]): LogisticRegressionModel ={

    val trainRDD = miscelaneaMap.get(m_KEY_RDDBINARY_TRAIN).getOrElse{
      println("ERROR: BenchmarkLogisticRegression.train in miscelaneaMap: None for key " + m_KEY_RDDBINARY_TRAIN
        + ". Creating empty RDD.")

      sc.emptyRDD[LabeledPoint] // empty RDD
    }.asInstanceOf[RDD[LabeledPoint]]  // convert Any (as returned by the HashMap) to a specific type

    val trainedModel = new LogisticRegressionWithLBFGS().setNumClasses(numClasses).run(trainRDD)

    return trainedModel

  }

  def predict(trainedModel: LogisticRegressionModel, miscelaneaMap: scala.collection.mutable.HashMap[String,Object]):
  RDD[(Double,Double)] = {

    val testRDD = miscelaneaMap.get(m_KEY_RDDBINARY_TEST).getOrElse{
      println("ERROR: BenchmarkLogisticRegression.predict in miscelaneaMap: None for key " + m_KEY_RDDBINARY_TEST
        + ". Creating empty RDD.")

      sc.emptyRDD[LabeledPoint] // empty RDD
    }.asInstanceOf[RDD[LabeledPoint]]  // convert Any (as returned by the HashMap) to a specific type
    trainedModel.clearThreshold
    val predictionAndLabels = testRDD.map { case LabeledPoint(label, features) =>
      val prediction = trainedModel.predict(features)
      (prediction, label)
    }
    print(predictionAndLabels.first())

    return predictionAndLabels
  }

  def getMetrics(trainedModel: LogisticRegressionModel, predictionsAndLabels:RDD[(Double,Double)]): List[(String,Any)]={

    var metricsSummary: List[(String, Any)] = List()
    // Multiclass Metrics

    val multiclassMetrics = new MulticlassMetrics(predictionsAndLabels)

    val confusionMatrix = multiclassMetrics.confusionMatrix

    metricsSummary = metricsSummary :+ ("confusionMatrixTP",confusionMatrix(1,1))
    metricsSummary = metricsSummary :+ ("confusionMatrixTN",confusionMatrix(0,0))
    metricsSummary = metricsSummary :+ ("confusionMatrixFP",confusionMatrix(0,1))
    metricsSummary = metricsSummary :+ ("confusionMatrixFN",confusionMatrix(1,0))

    // Overall Statistics
    val precision = multiclassMetrics.precision
    metricsSummary = metricsSummary :+ ("precision", precision)

    val recall = multiclassMetrics.recall // same as true positive rate
    metricsSummary = metricsSummary :+ ("recall", recall)

    val f1Score = multiclassMetrics.fMeasure
    metricsSummary = metricsSummary :+ ("fMeasure", f1Score)

    // Precision, Recall, FPR & F-measure by label
    val labels = multiclassMetrics.labels
    labels.foreach { l =>
      metricsSummary = metricsSummary :+ (s"Precision($l)", multiclassMetrics.precision(l))
      metricsSummary = metricsSummary :+ (s"Recall($l)", multiclassMetrics.recall(l))
      metricsSummary = metricsSummary :+ (s"FPR($l)", multiclassMetrics.falsePositiveRate(l))
      metricsSummary = metricsSummary :+ (s"F1-Score($l)", multiclassMetrics.fMeasure(l))
    }

    // Weighted stats
    val weightedPrecision = multiclassMetrics.weightedPrecision
    val weightedRecall = multiclassMetrics.weightedRecall
    val weightedF1Score = multiclassMetrics.weightedFMeasure
    val weightedFalsePositiveRate = multiclassMetrics.weightedFalsePositiveRate

    // Binary classification metrics, varying  threshold


    val binayMetrics = new BinaryClassificationMetrics(predictionsAndLabels)

    // AUPRC
    val binaryAUPRC = binayMetrics.areaUnderPR
    metricsSummary = metricsSummary :+ ("AUPRC", binaryAUPRC)

    // AUROC
    val binaryAUROC = binayMetrics.areaUnderROC
    metricsSummary = metricsSummary :+ ("AUROC", binaryAUROC)

    // Precision by threshold
    val binaryPrecision = binayMetrics.precisionByThreshold
    metricsSummary = metricsSummary :+ ("precisionByThreshold", binaryPrecision.collect())

    // Recall by threshold
    val binaryRecall = binayMetrics.recallByThreshold
    metricsSummary = metricsSummary :+ ("recallByThreshold", binaryRecall.collect())

    // Precision-Recall Curve
    val binaryPRC = binayMetrics.pr
    metricsSummary = metricsSummary :+ ("PRCByThreshold", binaryPRC.collect())

    // F-measure
    val binaryF1Score = binayMetrics.fMeasureByThreshold
    metricsSummary = metricsSummary :+ ("F1ScoreByThreshold", binaryF1Score.collect())

    val beta = 0.5
    val binaryFScore = binayMetrics.fMeasureByThreshold(beta)
    metricsSummary = metricsSummary :+ ("FScoreByThreshold", binaryFScore.collect())

    // Compute thresholds used in ROC and PR curves
    val binaryThresholds = binaryPrecision.map(_._1)
    metricsSummary = metricsSummary :+ ("Thresholds", binaryThresholds.collect())

    // ROC Curve
    val binaryRoc = binayMetrics.roc
    metricsSummary = metricsSummary :+ ("ROCByThreshold", binaryRoc.collect())

    return metricsSummary

  }

}