package com.sztu.spring;

import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

@Component
@Import(ImportTest2.class)
public class ImportTest {
}
