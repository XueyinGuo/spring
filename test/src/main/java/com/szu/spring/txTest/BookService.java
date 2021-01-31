package com.szu.spring.txTest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

//@Service
public class BookService {

	@Autowired
	BookDao bookDao;


	public void checkout(String userName, int id){
		bookDao.updateStock(id);
//		int price = bookDao.getPrice(id);
//		bookDao.updateBalance(userName, price);
	}


	public BookDao getBookDao() {
		return bookDao;
	}

	public void setBookDao(BookDao bookDao) {
		this.bookDao = bookDao;
	}
}
