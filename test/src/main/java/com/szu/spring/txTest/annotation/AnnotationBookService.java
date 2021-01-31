package com.szu.spring.txTest.annotation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnotationBookService {




	@Autowired
	private AnnotationBookDao annotationBookDao;


	@Transactional
	public void checkout(String userName, int id){
		annotationBookDao.updateStock(id);
		int price = annotationBookDao.getPrice(id);
		annotationBookDao.updateBalance(userName, price);
	}


	public AnnotationBookDao getAnnotationBookDao() {
		return annotationBookDao;
	}

	public void setAnnotationBookDao(AnnotationBookDao annotationBookDao) {
		this.annotationBookDao = annotationBookDao;
	}
}
