package com.pack;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TransactionData  implements Serializable {
	private static final long serialVersionUID = 1L;
	
	@JsonProperty("card_id")
	private Long cardId;
	
	@JsonProperty("member_id")
	private Long memberId;
	
	@JsonProperty("amount")
	private Double amount;
	
	@JsonProperty("pos_id")
	private Long posId;
	
	@JsonProperty("postcode")
	private Integer postcode;
	
	@JsonProperty("transaction_dt")
	private String transactionDate;
	
    public TransactionData(){
    }
    
    public TransactionData(long cardId, long memberId, double amount, long posId, int postcode, String transactionDate) {
        super();
        this.cardId = cardId;
        this.memberId = memberId;
        this.amount = amount;
        this.posId = posId;
        this.postcode = postcode;
        this.transactionDate = transactionDate;
    }
	
	public Long getCardId() {
		return cardId;
	}
	
	public Long getMemberId() {
		return memberId;
	}
	
	public Double getAmount() {
		return amount;
	}
	
	public Long getPosId() {
		return posId;
	}
	
	public Integer getPostcode() {
		return postcode;
	}
	
	public String getTransactionDate() {
		return transactionDate;
	}
	
	public void setCardId(long card_id) {
		this.cardId = card_id;
	}
	
	public void setMemberId(long member_id) {
		this.memberId = member_id;
	}
	
	public void setAmount(double amount) {
		this.amount = amount;
	}
	
	public void setPosId(long posId) {
		this.posId = posId;
	}
	
	public void setPostcode(int postcode) {
		this.postcode = postcode;
	}
	
	public void setTransactionDate(String transactionDate) {
		this.transactionDate = transactionDate;
	}
	
	@Override
	public String toString()
	{
		return this.cardId + "," + this.amount + "," + this.memberId + "," + this.posId + "," + this.postcode + "," + this.transactionDate;
	}
}
