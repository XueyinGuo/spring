package com.szu.spring.txTest.annotation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AnnotationBookDao {

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Transactional
	public void updateBalance(String userName, int price){
		String sql = "update account set balance = balance - ? where username = ?";
		jdbcTemplate.update(sql);
	}

	@Transactional
	public int getPrice(int id){
		String sql = "select price from book where id = ?";
		Integer integer = jdbcTemplate.queryForObject(sql, Integer.class);
		return integer;
	}

	@Transactional
	public void updateStock(int id){
		String sql = "update book_stock set stock=stock-1 where id = ?";
		jdbcTemplate.update(sql);
	}

	public JdbcTemplate getJdbcTemplate() {
		return jdbcTemplate;
	}

	public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
//		int i = 1/10;
	}
}
