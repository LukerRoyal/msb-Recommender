package recall

import util.SparkSessionBase

object Zip {
  def main(args: Array[String]): Unit = {
    val session = SparkSessionBase.createSparkSession()
    val context = session.sparkContext
    val rdd = context.makeRDD(List(1,2,3,4,5,6,7,8),2)


    rdd.zipWithIndex().foreach(println)


    rdd.mapPartitionsWithIndex((index,partitoin) =>{
      println("partitonID:" + index)
      partitoin.foreach(println)
      partitoin
    }).count()






    rdd.zipWithUniqueId().foreach(println)


    context.stop()


  }
}
