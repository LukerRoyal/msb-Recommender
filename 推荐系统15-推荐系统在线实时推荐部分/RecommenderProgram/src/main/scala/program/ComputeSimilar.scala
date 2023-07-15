package program

import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.ml.feature.{BucketedRandomProjectionLSH, Word2VecModel}
import org.apache.spark.ml.linalg.{DenseVector, SparseVector}
import org.apache.spark.sql.{Row, SaveMode}
import util.{HBaseUtil, PropertiesUtils, SparkSessionBase}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * ntpdate ntp1.aliyun.com
  */
object ComputeSimilar {


  def main(args: Array[String]): Unit = {

    val session = SparkSessionBase.createSparkSession()
    session.sql("use tmp_program")
    import session.implicits._
    //    val keyWord2WeightDF = session.table("keyword_tr").limit(1000)
    val keyWord2WeightDF = session.table("keyword_tfidf")

    val word2Weight = keyWord2WeightDF.rdd.map(row => {
      val itemID = row.getAs[Int]("item_id")
      val word = row.getAs[String]("word")
      val tr = row.getAs[Double]("tfidf")
      //itemID + "_" + word   因为不同节目中 关键词所对应的tf-idf是不一样的
      (itemID + "_" + word, tr)
    }).collect().toMap

    //Executor中创建一个广播变量   Executor一个广播变量副本
    val word2WeightBroad = session.sparkContext.broadcast(word2Weight)


    val word2VecModel = Word2VecModel.load("hdfs://node01:9000/recommond_program/models/w2v.model")
    val word2VecMap = word2VecModel.getVectors.collect().map(row => {
      val vector = breeze.linalg.DenseVector(row.getAs[DenseVector]("vector").toArray)
      val word = row.getAs[String]("word")
      (word, vector)
    }).toMap
    val word2VecMapBroad = session.sparkContext.broadcast(word2VecMap)


    val word2Index = session.table("keyword_idf").rdd.map(row => {
      val index = row.getAs[Int]("index")
      val word = row.getAs[String]("word")
      (word, index)
    }).collectAsMap()
    val word2IndexBroad = session.sparkContext.broadcast(word2Index)


    //    val keyWordDF = session.table("item_keyword")
    val keyWordDF = session.sql("select * from item_keyword")
    val featuresDF = keyWordDF.map(row => {
      val word2VecMap = word2VecMapBroad.value
      val word2Weight = word2WeightBroad.value
      val word2Index = word2IndexBroad.value
      val itemID = row.getAs[Int]("item_id")
      val keywords = row.getAs[Seq[String]]("keyword")
      var index = 0
      val indexs = new mutable.HashSet[Int]()
      val values = new ArrayBuffer[Double]()
      for (word <- keywords) {
        //tf-idf
        val weight = word2Weight.getOrElse(itemID + "_" + word, 1.0)
        var nWht = 0d
        if (word2VecMap.contains(word)) {
          val trScore = word2VecMap(word)
          val newVector = trScore * weight
          nWht = newVector.toArray.sum / newVector.length
        } else {
          nWht = weight
        }
        if (word2Index.contains(word)) {
          indexs += (word2Index.get(word).get)
          values += (nWht)
        }
      }
      val sortIndex = indexs.toArray.sorted
      val vector = new SparseVector(word2Index.size, sortIndex, values.toArray)
      println("vector.size:" + vector.size)

      (itemID, vector.toDense)
    }
    ).toDF("item_id", "features")

    //将每个节目所对应的向量存储到Hive中
    featuresDF.write.mode(SaveMode.Overwrite).saveAsTable("tmp_keyword_weight")

    featuresDF.rdd
      .foreachPartition(partition => {
        val conf = HBaseUtil.getHBaseConfiguration()
        val htable = HBaseUtil.getTable(conf,"keyword_weight")
        for (row <- partition) {
            val itemID = row.getAs[Int]("item_id")
            val features = row.getAs[DenseVector]("features")
              val put = new Put(Bytes.toBytes(itemID + ""))
              println("features.size:" + features.size)
              put.addColumn(Bytes.toBytes("features"), Bytes.toBytes("features"), Bytes.add(Bytes.toByteArrays(features.toDense.toArray.map(x=> x+"\t"))))
              htable.put(put)
          }
      })

    val rddArr = featuresDF.randomSplit(Array(0.7, 0.3))
    val train = rddArr(0)
    val test = rddArr(1)

    val brpls = new BucketedRandomProjectionLSH()
    brpls.setInputCol("features")
    brpls.setOutputCol("hashes")
    //桶个数
    brpls.setBucketLength(10.0)
    val model = brpls.fit(train)

    val similar = model.approxSimilarityJoin(featuresDF, featuresDF, 2.0, "EuclideanDistance")

    similar.show(10,false)
//    create 'program_similar',{NAME => 'similar', VERSIONS => 9999}

    val tableName = PropertiesUtils.getProp("similar.hbase.table")
    similar.toDF()
      .rdd
      .foreachPartition(partition => {
        val conf = HBaseUtil.getHBaseConfiguration()
//        conf.set(TableOutputFormat.OUTPUT_TABLE, tableName)
        val htable = HBaseUtil.getTable(conf,tableName)
        for (row <- partition) {
          if (row.getAs[Double]("EuclideanDistance") < 1) {
            val aItemID = row.getAs[Row]("datasetA").getAs[Int](0)
            val bItemID = row.getAs[Row]("datasetB").getAs[Int](0)
            val dist = row.getAs[Double]("EuclideanDistance")
            if (aItemID != bItemID) {
              val put = new Put(Bytes.toBytes(aItemID + ""))
              put.addColumn(Bytes.toBytes("similar"), Bytes.toBytes(bItemID + ""), Bytes.toBytes(dist + ""))
              htable.put(put)
            }
          }
        }
      })
    session.close()
  }
}
