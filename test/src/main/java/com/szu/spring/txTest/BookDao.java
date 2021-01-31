package com.szu.spring.txTest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

//@Repository
public class BookDao {
	@Autowired
	JdbcTemplate jdbcTemplate;


	public void updateBalance(String userName, int price){
		String sql = "update account set balance = balance - " + price +  " where username =  '"+ userName + "'";
		jdbcTemplate.update(sql);
	}


	public int getPrice(int id){
		String sql = "select price from book where id = "+ id;
		Integer integer = jdbcTemplate.queryForObject(sql, Integer.class);
		return integer;
	}


	public void updateStock(int id){
		String sql = "update book_stock set stock=stock-1 where id = "+ id;
		jdbcTemplate.update(sql);
		for (int i = 1; i >=0 ; i--) {
//			System.out.println(1/i);
		}
	}

	public JdbcTemplate getJdbcTemplate() {
		return jdbcTemplate;
	}

	public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}
}
