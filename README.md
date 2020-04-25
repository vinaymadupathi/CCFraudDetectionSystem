#Develop a pipeline using Hadoop ecosystem to detect credit card fraud transactions

Input data
===========
1. card_member - card-id, member_id, member_joining_dt, card_purchase_dt, country, city
2. member_score - member_id, score
3. card_transactions - card-id, member_id, amount, postcode, pos_id, transaction_dt, status
4. Transactional payload(data) - Sent from pos terminals to kafka topic - Real time streaming data
card-id, member_id, amount, pos_id, postcode, transaction_dt

Architecture and approach
========================================
1. Ingest data from aws RDS using sqoop - card_member and member_score tables
	a. Sqoop commands to ingest to hadoop - done
	b. Incremental ingest - using jobs may be
	
2. Load card_transactions table into NoSql db i.e., Hbase
	a. Copy card_transactions from local to hdfs
	b. Create hive table and load data
	c. Create hive-hbase integrated table using rowkey defined
	d. Create a lookup table in hbase - Use a NoSQL database which gives schema evolution, schema versioning, row-level lookups (efficient reads), and tunable consistency. 
		
3. Calculate three parameters
	a. Upper Control Limit (UCL) - Moving average + 3*Standard Deviation
			Moving average and standard deviation is calculated based on last 10 amount credited with Genuine
		->card_transactions - for last 10 transactions
		->At regular intervals- lookup table needs to be updated with ucl values for each card-id
		
		Steps
		Create raw table in hive containing card_transactions data
		Calculate moving average and standard deviation in hive and store in next staging table
		Calculate ucl value in hive and store in next final table
		Then store ucl value in hbase lookup table
		
	b. Credit score - If score < 200 , transaction is fraudulent
		Need to be updated every 4 hours
		
	c. zip code distance - distance of current and last transaction wrt time 
		Need to use post code library provided
		Store post_code and transaction_dt of last transaction in lookup table		

3. Use SparkStreaming Connect to kakfa topic to get the streaming data i.e., transactional payload (data)
4. Query lookup table to check the rules to find transaction is fraudelent or not in seconds SLA
	a. Retrieve postcode and transaction_dt from lookup and compare with current postcode and transaction_dt. 
		Use postcode library provided to calculate the speed. If its ahead of imaginable speed it is fraud.
	b. Writes record with status whether Genuine or Fraudulent to card_transactions table of hive/hbase
	c. Update zip code and date time in lookup table only if current transaction if genuine

