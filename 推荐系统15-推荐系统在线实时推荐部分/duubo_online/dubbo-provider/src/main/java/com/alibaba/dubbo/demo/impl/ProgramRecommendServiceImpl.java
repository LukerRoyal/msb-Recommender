package com.alibaba.dubbo.demo.impl;

import com.alibaba.dubbo.demo.ProgramRecommendService;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.spark.ml.linalg.SparseVector;

import java.io.IOException;
import java.util.*;

public class ProgramRecommendServiceImpl implements ProgramRecommendService {

    public List<String> getProgramRecommendList(String uid) {
        String min = DateUtil.getMin(new Date());
        RedisUtil.init("node01",6379);
        //先去redis查询
        Set<String> recommendSet = RedisUtil.getMembers(3, uid+"-"+min);
        if(recommendSet.size() > 0){
            System.out.println("from redis");
            return new ArrayList<>(recommendSet);
        }
//        LogisticRegressionModel regressionModel = LogisticRegressionModel.load("hdfs://node01:9000/recommond_program/models/lrModel.model");

        //用于存储用户召回结果
        ArrayList<String> recalls = new ArrayList<>();
        //拿到这个用户的召回结果  item1，item2，item3
        Result recall = getRecord(uid, "recall");
        recalls.addAll(getRecallRest(recall, "als", "item", "\\|"));
        recalls.addAll(getRecallRest(recall, "online", "item", "\\|"));
        recalls.addAll(getRecallRest(recall, "content", "item", "\\|"));

//       多大, recall size = score.size
        HashMap<String, Double> scores = new HashMap<>();
        for (String item : recalls) {
            //getVector  构建测试样本
            List<String> vectorList = getVector(uid, item);
            //getScore 基于测试样本和model计算score
            double score = getScore(vectorList);
            scores.put(item,score);
        }
        ArrayList<Map.Entry<String, Double>> sortScores = new ArrayList<>(scores.entrySet());
        Collections.sort(sortScores, new Comparator<Map.Entry<String, Double>>() {
            @Override
            public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
                if( o1.getValue() - o2.getValue() >= 0){
                    return -1;
                }else {
                    return 1;
                }
            }
        });
        //获取topN推荐结果
        ArrayList<String> recommends = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            String item = sortScores.get(index).getKey();
            recommends.add(item);
            //将这个用户的推荐结果存储到redis中  key：uid+"-"+min
            RedisUtil.insertRecommend(3,uid+"-"+min,item);
            System.out.println(item);
        }
        return recommends;
    }

    //计算评分
    private double getScore(List<String> vectorList) {
        double score = 0.0;

        for (int index = 0; index < vectorList.size(); index++) {
            double value = Double.parseDouble(vectorList.get(index));
            if(value != 0){
                double weight = Double.parseDouble(RedisUtil.getRecord(3, "model", index+""));
                score += weight * value;
            }
        }
        return score;
    }

    //构建测试样本
    public List<String> getVector(String uid, String item){
            String rowKey = uid + ":" + item;
            System.out.println("rowKey：" + rowKey);
            Result result = getRecord(rowKey, "ctr_feature");
            List<String> rest = getRecallRest(result, "feature", "feature", "\t");
            if (rest.size() == 0) {
                //获取节目画像
                Result itemResult = getRecord(item, "keyword_weight");
                List<String> recallRest = getRecallRest(itemResult, "features", "features", "\t");
//                System.out.println("recallRest.size():" + recallRest.size());
                rest.addAll(recallRest);


                //用户画像：获取当前用户的省份和城市
                Result profileResult = getRecord(uid, "user_profile");
                String province = getRecallRest(profileResult, "info", "province", "\t").get(0);
                //未来构建的向量多长
                int provinceCount = RedisUtil.getRecordCount(5, "province");
                //通过省份去redis中查找下标，未来构建向量的适合，哪个位置填1
                String provinceIndex = RedisUtil.getRecord(5, "province", province);
                SparseVector provinceVector = new SparseVector(provinceCount, new int[]{Integer.parseInt(provinceIndex)}, new double[]{1});
                double[] toArray = provinceVector.toArray();
                for (double v : toArray) {
                    rest.add(v + "");
                }
//                System.out.println("provinceVector.size():" + provinceVector.size());

                String city = getRecallRest(profileResult, "info", "city", "\t").get(0);
                int cityCount = RedisUtil.getRecordCount(5, "city");
                String cityIndex = RedisUtil.getRecord(5, "city", city);
                SparseVector cityVector = new SparseVector(cityCount, new int[]{Integer.parseInt(cityIndex)}, new double[]{1});
                double[] toArray2 = cityVector.toArray();
                for (double v : toArray2) {
                    rest.add(v + "");
                }
//                System.out.println("cityVector.size():" + cityVector.size());

                //用户画像的行为数据部分
                int recordCount = RedisUtil.getRecordCount(5, "kw");
                SparseVector actionVector = new SparseVector(recordCount, new int[]{1}, new double[]{0.0});
//                System.out.println("actionVector.size():" + actionVector.size());
                double[] toArray3 = actionVector.toArray();
                for (double v : toArray3) {
                    rest.add(v + "");
                }
//                System.out.println("actionVector.size():" + actionVector.size());
            }
            return rest;
        }


        private static List<String> getRecallRest (Result result, String family, String columnName, String delimiter){
            byte[] recall = result.getValue(family.getBytes(), columnName.getBytes());
            ArrayList<String> list = new ArrayList<>();
            if (recall != null) {
                String alsRecall = new String(recall);
                list.addAll(Arrays.asList(alsRecall.split(delimiter)));
            }
            return list;
        }

        private static Result getRecord (String uid, String tableName){
            Configuration conf = HBaseConfiguration.create();
            conf.set("hbase.zookeeper.quorum", "node01,node02,node03");
            conf.set("hbase.zookeeper.property.clientPort", "2181");
            Connection conn = null;
            Table table = null;
            Result result = null;
            try {
                conn = ConnectionFactory.createConnection(conf);
                table = conn.getTable(TableName.valueOf(tableName));
                byte[] row = new byte[]{};
                row = uid.getBytes();
                Get g = new Get(row);
                result = table.get(g);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return result;
        }
    }
