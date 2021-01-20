package com.sztu.spring.txTest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BookService {

	@Autowired
	BookDao bookDao;


	public void checkout(String userName, int id){
		bookDao.updataStock(id);
		int price = bookDao.getPrice(id);
		bookDao.updateBalance(userName, price);
	}


	public BookDao getBookDao() {
		return bookDao;
	}

	public void setBookDao(BookDao bookDao) {
		this.bookDao = bookDao;
	}
}
