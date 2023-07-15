package util

import org.apache.spark.rdd.RDD

object AggregateByKey {
  def main(args: Array[String]): Unit = {
    val session = SparkSessionBase.createSparkSession()
    val sc = session.sparkContext

    val rdd = sc.parallelize(List(("a",1),("b",2),("b",3),("a",3),("b",4),("a",5)),2)

    //柯里化函数
//    val agg = rdd.aggregateByKey(0)(math.max(_,_),_+_)
    val aggRDD: RDD[(String, Int)] = rdd.aggregateByKey(0)((x,y)=>{math.max(x,y)},(x,y)=>{x+y})

    aggRDD.collect().foreach(println)

    session.stop()
  }
}
