package com.telusko.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

//@Component
public class Product {

	public Product(int prodId, String prodName, int price) {
			super();
			this.prodId = prodId;
			this.prodName = prodName;
			this.price = price;
	}
	  
    private int prodId;
    private String prodName;
    private int price;
    
	public int getProdId() {
		return prodId;
	}
	public void setProdId(int prodId) {
		this.prodId = prodId;
	}
	
	public String getProdName() {
		return prodName;
	}
	public void setProdName(String prodName) {
		this.prodName = prodName;
	}
	
	public int getPrice() {
		return price;
	}
	public void setPrice(int price) {
		this.price = price;
	}
	
}
