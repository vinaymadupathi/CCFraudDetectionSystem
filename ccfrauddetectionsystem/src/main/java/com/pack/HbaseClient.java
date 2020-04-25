package com.pack;

import java.io.IOException;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;

public class HbaseClient {
	private static Connection hBaseConnection;
	private static String CARD_TRANSACTION_MASTER_TABLE = "card_transactions_master";
	private static String CARD_TRANSACTION_LOOKUP_TABLE = "card_transaction_lookup";
	
	public static Connection getHbaseConnection(String hBaseMaster) throws IOException {
		try {
			
			if (hBaseConnection == null || hBaseConnection.isClosed()) {
				
				org.apache.hadoop.conf.Configuration conf = (org.apache.hadoop.conf.Configuration) HBaseConfiguration.create();
				conf.setInt("timeout", 1200);
				conf.set("hbase.master", hBaseMaster + ":60000");
				conf.set("hbase.zookeeper.quorum", hBaseMaster);
				conf.set("hbase.zookeeper.property.clientPort", "2181");
				conf.set("zookeeper.znode.parent", "/hbase");
				
				hBaseConnection = ConnectionFactory.createConnection(conf);
				}
		}catch (Exception e) {
			e.printStackTrace();
		}

		return hBaseConnection;
	}
	
	public static TransactionLookupRecord getTransactionLookupRecord(TransactionData transactionData, String hBaseMaster) throws IOException {
		Connection connection = HbaseClient.getHbaseConnection(hBaseMaster);
		Table hBaseLookupTable = null;
		
		try {		
			Get cardIdKey = new Get(Bytes.toBytes(transactionData.getCardId().toString()));
			hBaseLookupTable = connection.getTable(TableName.valueOf(HbaseClient.CARD_TRANSACTION_LOOKUP_TABLE));
			
			Result result = hBaseLookupTable.get(cardIdKey);
			TransactionLookupRecord record = new TransactionLookupRecord(transactionData.getCardId());
			
			byte[] value = result.getValue(Bytes.toBytes("cf"), Bytes.toBytes("ucl"));
			if (value != null) {
				record.setUcl(Double.parseDouble(Bytes.toString(value)));
			}
			
			value = result.getValue(Bytes.toBytes("cf"), Bytes.toBytes("score"));
			if (value != null) {
				record.setScore(Integer.parseInt(Bytes.toString(value)));
			}
			
			value = result.getValue(Bytes.toBytes("cf"), Bytes.toBytes("postcode"));
			if (value != null) {
				record.setPostcode(Integer.parseInt(Bytes.toString(value)));
			}
			
			value = result.getValue(Bytes.toBytes("cf"), Bytes.toBytes("transaction_dt"));
			if (value != null) {
				record.setTransactionDate(Bytes.toString(value));
			}
			
			return record;
			
		} catch (Exception e) {
		e.printStackTrace();
		} 
		finally {
			try {
				if (hBaseLookupTable != null)
					hBaseLookupTable.close();
				
				if(connection != null)
					connection.close();
			} 
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}
	
	public static void InsertTransactionIntoDB(TransactionData transactionData, String hBaseMaster, String status) throws IOException {
		Connection connection = HbaseClient.getHbaseConnection(hBaseMaster);
		Table hbaseMasterTable = null;
		Table hbaseLookupTable = null;
		
		try {

			// Add transaction to card_transactions_master table in hbase
			hbaseMasterTable = connection.getTable(TableName.valueOf(HbaseClient.CARD_TRANSACTION_MASTER_TABLE));
			
			String guid = java.util.UUID.randomUUID().toString().replace("-", "");
			Put put = new Put(Bytes.toBytes(guid));
			put.addColumn(Bytes.toBytes("cardDetail"), Bytes.toBytes("card_id"), Bytes.toBytes(transactionData.getCardId().toString()));
			put.addColumn(Bytes.toBytes("cardDetail"), Bytes.toBytes("member_id"), Bytes.toBytes(transactionData.getMemberId().toString()));
			put.addColumn(Bytes.toBytes("transactionDetail"), Bytes.toBytes("amount"), Bytes.toBytes(transactionData.getAmount().toString()));
			put.addColumn(Bytes.toBytes("transactionDetail"), Bytes.toBytes("postcode"), Bytes.toBytes(transactionData.getPostcode().toString()));
			put.addColumn(Bytes.toBytes("transactionDetail"), Bytes.toBytes("pos_id"), Bytes.toBytes(transactionData.getPosId().toString()));
			put.addColumn(Bytes.toBytes("transactionDetail"), Bytes.toBytes("transaction_dt"), Bytes.toBytes(transactionData.getTransactionDate()));
			put.addColumn(Bytes.toBytes("transactionDetail"), Bytes.toBytes("status"), Bytes.toBytes(status));			
			
			hbaseMasterTable.put(put);
			
			// Update current postcode and transaction date in card_transaction_lookup for corresponding card_id only if current transaction is genuine
			if(status.equalsIgnoreCase("GENUINE"))
			{
				hbaseLookupTable = connection.getTable(TableName.valueOf(HbaseClient.CARD_TRANSACTION_LOOKUP_TABLE));
				put = new Put(Bytes.toBytes(transactionData.getCardId().toString()));
				put.addColumn(Bytes.toBytes("cf"), Bytes.toBytes("postcode"), Bytes.toBytes(transactionData.getPostcode().toString()));
				put.addColumn(Bytes.toBytes("cf"), Bytes.toBytes("transaction_dt"), Bytes.toBytes(transactionData.getTransactionDate().toString()));
				hbaseLookupTable.put(put);
			}
			
		} catch (Exception e) {
		e.printStackTrace();
		} 
		finally {
			try {
				if (hbaseMasterTable != null)
					hbaseMasterTable.close();
				
				if (hbaseLookupTable != null)
					hbaseLookupTable.close();
				
				if(connection != null)
					connection.close();
			} 
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
