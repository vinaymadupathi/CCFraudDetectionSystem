package com.pack;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.connect.json.JsonDeserializer;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.PairFunction;
import org.apache.spark.api.java.function.VoidFunction;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka010.ConsumerStrategies;
import org.apache.spark.streaming.kafka010.KafkaUtils;
import org.apache.spark.streaming.kafka010.LocationStrategies;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import scala.Tuple2;

// Main driver class for Credit Card Fraud Detection system
public class CCFraudDetectionSystem {

	public static void main(String[] args) throws Exception {
		
    	// Verify if required arguments such as broker id, topic, group id, zipcode csv path, hbasemaster are provided, 
		// otherwise exit with error 
        if (args.length < 5) {
            System.err.println("Usage: CCFraudDetectionSystem <broker id> <topic> <groupId> <zipCodecsvpath> <hbaseMaster> \n");
            System.exit(1);
        }
		
		// Take inputs from command line arguments - broker, topic, groupId, zipcode csv path and hbasemaster
		String broker = args[0];
		String topic = args[1];		
		String groupId = args[2];
		String zipCodePosIdCsvPath = args[3];
		String hBaseMaster = args[4];
		
		// Create Spark Configuration and java streaming context object with batch interval of 1 minute
		SparkConf conf = new SparkConf().setAppName("CCFraudDetectionSystem");//.setMaster("local[*]");
		JavaStreamingContext context = new JavaStreamingContext(conf, Durations.seconds(1));
		
		// Set log level to WARN
		context.sparkContext().setLogLevel("WARN");
	
		// Create topic set by splitting based on comma if multiple topics are provided
		Set<String> topicSet = new HashSet<String>(Arrays.asList(topic));
		
		// Create kafka parameters map with configurations - brokers, group id, key and value deserializer classes
		Map<String,Object> kafkaParams = new HashMap<String, Object>();
		kafkaParams.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker);
		kafkaParams.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
		kafkaParams.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		kafkaParams.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
		kafkaParams.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
		kafkaParams.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		
		// Create Kafka integration with Spark Streaming subscribing to specified topic set and kafka parameters to create DStreams 
		JavaInputDStream<ConsumerRecord<String, JsonNode>> messages = KafkaUtils.createDirectStream(context, 
				LocationStrategies.PreferConsistent(), ConsumerStrategies.Subscribe(topicSet, kafkaParams));		
      
		// Using objectmapper, extract data into objects of type TransactionData
        JavaDStream<TransactionData> transactionData = messages.map(new Function<ConsumerRecord<String, JsonNode>, TransactionData>() {
            private static final long serialVersionUID = 1L;

            @Override
            public TransactionData call(ConsumerRecord<String, JsonNode> record) throws JsonProcessingException {
                ObjectMapper objectMapper = new ObjectMapper();
                return objectMapper.treeToValue(record.value(), TransactionData.class);
            }
        });

        // Create distance utility by loadingn zipCodeposId csv provided
        //DistanceUtility distanceUtility = new DistanceUtility(zipCodePosIdCsvPath);
        
        // For each transaction Data perform below operations
        // 1. Get corresponding record from look up table in HBase i.e., card_transaction_lookup based on card_id
        // 2. Calculate Speed per second i.e., zipcodedistance/(current transaction datetime - previous transaction datetime). 
        //    Get Zipcodedistance using DistanceUtility.cs provided
        // 3. Check if either creditscore less than 200 or if transaction amount exceeds UCL or if speed is greater than 0.24 considering 1 KM per sec
        //    transaction is fraudulent, otherwise it is genuine.
        // 4. Call InsertTransactionIntoDB which inserts current transactionData to master table in HBase i.e., card_transactions_master 
        //    and updates postcode, transaction_dt into look up table in HBase i.e., card_transaction_lookup only if status is Genuine.
        transactionData.foreachRDD(new VoidFunction<JavaRDD<TransactionData>>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void call(JavaRDD<TransactionData> rdd) {
            	
            	JavaPairRDD<TransactionData, String> transactionDataWithStatus = rdd.mapToPair(new PairFunction<TransactionData, TransactionData, String>() {
					private static final long serialVersionUID = 1L;

					@Override
					public Tuple2<TransactionData, String> call(TransactionData transactionData) throws Exception {
	                	// 1. Get corresponding record from look up table in HBase i.e., card_transaction_lookup based on card_id
	                	TransactionLookupRecord record = HbaseClient.getTransactionLookupRecord(transactionData, hBaseMaster);
	                	
	                    // 2. Calculate Speed per second i.e., zipcodedistance/(current transaction datetime - previous transaction datetime). 
	                    //    Get Zipcodedistance using DistanceUtility.cs provided
	            		double zipcodeDistance = DistanceUtility.GetInstance(zipCodePosIdCsvPath)
	            				.getDistanceViaZipCode(transactionData.getPostcode().toString(), record.getPostcode().toString());
	            		
	            		SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
	            		Date previousTransactionDate = format.parse(record.getTransactionDate());
	            		Date currentTransactionDate = format.parse(transactionData.getTransactionDate());
	            		long diffSeconds = (currentTransactionDate.getTime() - previousTransactionDate.getTime()) / 1000; // difference in seconds
	            		double speed = zipcodeDistance/diffSeconds;
	            		
	            		// 3. If Credit score is less than 200, or if transaction amount exceeds UCL or if speed is greater than 0.24 considering 1 KM per 4 sec
	            		// considering transaction as fraudulent otherwise genuine
	            		String status = "GENUINE";
	            		if(record.getScore() < 200 || transactionData.getAmount() > record.getUcl() || speed > 0.25)
	            		{
	            			status = "FRAUD";
	            		}
	            		
	                    // 4. Call InsertTransactionIntoDB which inserts current transactionData to master table in HBase i.e., card_transactions_master 
	                    //    and updates postcode, transaction_dt into look up table in HBase i.e., card_transaction_lookup only if status is Genuine.
	                	HbaseClient.InsertTransactionIntoDB(transactionData, hBaseMaster, status);
	            		
	            		return new Tuple2<TransactionData, String>(transactionData, status);
					}            		
            	});
            	
            	transactionDataWithStatus.collect().forEach(t -> 
                {            		
                	System.out.println(t._1.toString() + " - " + t._2);
                });
            }
        });

        context.start();
        context.awaitTermination();
	}

}
