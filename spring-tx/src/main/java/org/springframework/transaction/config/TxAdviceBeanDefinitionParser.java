/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.transaction.config;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.NoRollbackRuleAttribute;
import org.springframework.transaction.interceptor.RollbackRuleAttribute;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;

/**
 * {@link org.springframework.beans.factory.xml.BeanDefinitionParser
 * BeanDefinitionParser} for the {@code <tx:advice/>} tag.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Adrian Colyer
 * @author Chris Beams
 * @since 2.0
 */
class TxAdviceBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

	private static final String METHOD_ELEMENT = "method";

	private static final String METHOD_NAME_ATTRIBUTE = "name";

	private static final String ATTRIBUTES_ELEMENT = "attributes";

	private static final String TIMEOUT_ATTRIBUTE = "timeout";

	private static final String READ_ONLY_ATTRIBUTE = "read-only";

	private static final String PROPAGATION_ATTRIBUTE = "propagation";

	private static final String ISOLATION_ATTRIBUTE = "isolation";

	private static final String ROLLBACK_FOR_ATTRIBUTE = "rollback-for";

	private static final String NO_ROLLBACK_FOR_ATTRIBUTE = "no-rollback-for";


	@Override
	protected Class<?> getBeanClass(Element element) {
		return TransactionInterceptor.class; /* 返回一个实现了 MethodInterceptor的接口 类型 */
	}

	@Override
	protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
		builder.addPropertyReference("transactionManager", TxNamespaceHandler.getTransactionManagerName(element));
		/*
		* 获取 tx:advice 的子元素
		* */
		List<Element> txAttributes = DomUtils.getChildElementsByTagName(element, ATTRIBUTES_ELEMENT);
		if (txAttributes.size() > 1) {
			parserContext.getReaderContext().error(
					"Element <attributes> is allowed at most once inside element <advice>", element);
		}
		else if (txAttributes.size() == 1) {
			/*
			* 创建一个新的 BeanDefinition中的一个 map 存放的都是 <tx:advice> 中 <tx:attributes> 中的所有的
			* tx:method 及其所对应的所有属性
			* */
			Element attributeSourceElement = txAttributes.get(0);
			RootBeanDefinition attributeSourceDefinition = parseAttributeSource(attributeSourceElement, parserContext);
			builder.addPropertyValue("transactionAttributeSource", attributeSourceDefinition); /* 把 TX 类型的beanDefinition中加入刚刚解析出来的所有的 Method */
		}
		else {
			// Assume annotations source.
			builder.addPropertyValue("transactionAttributeSource",
					new RootBeanDefinition("org.springframework.transaction.annotation.AnnotationTransactionAttributeSource"));
		}
	}

	private RootBeanDefinition parseAttributeSource(Element attrEle, ParserContext parserContext) {
		/*
		* 获取 <tx:attributes> 中所有的 <tx:method
		* */
		List<Element> methods = DomUtils.getChildElementsByTagName(attrEle, METHOD_ELEMENT);
		ManagedMap<TypedStringValue, RuleBasedTransactionAttribute> transactionAttributeMap =
				new ManagedMap<>(methods.size());
		transactionAttributeMap.setSource(parserContext.extractSource(attrEle));
		/*
		 * tx:advice 中 tx:attributes 中的所有的 tx:method
		 */
		for (Element methodEle : methods) {
			String name = methodEle.getAttribute(METHOD_NAME_ATTRIBUTE);
			TypedStringValue nameHolder = new TypedStringValue(name);
			nameHolder.setSource(parserContext.extractSource(methodEle));

			RuleBasedTransactionAttribute attribute = new RuleBasedTransactionAttribute();
			String propagation = methodEle.getAttribute(PROPAGATION_ATTRIBUTE);              /* 传播特性 */
			String isolation = methodEle.getAttribute(ISOLATION_ATTRIBUTE);                 /* 隔离级别 */
			String timeout = methodEle.getAttribute(TIMEOUT_ATTRIBUTE);                    /* 超时时间 */
			String readOnly = methodEle.getAttribute(READ_ONLY_ATTRIBUTE);                /* 是否只读 */
			if (StringUtils.hasText(propagation)) {
				attribute.setPropagationBehaviorName(RuleBasedTransactionAttribute.PREFIX_PROPAGATION + propagation);
			}
			if (StringUtils.hasText(isolation)) {
				attribute.setIsolationLevelName(RuleBasedTransactionAttribute.PREFIX_ISOLATION + isolation);
			}
			if (StringUtils.hasText(timeout)) {
				try {
					attribute.setTimeout(Integer.parseInt(timeout));
				}
				catch (NumberFormatException ex) {
					parserContext.getReaderContext().error("Timeout must be an integer value: [" + timeout + "]", methodEle);
				}
			}
			if (StringUtils.hasText(readOnly)) {
				attribute.setReadOnly(Boolean.parseBoolean(methodEle.getAttribute(READ_ONLY_ATTRIBUTE)));
			}

			List<RollbackRuleAttribute> rollbackRules = new ArrayList<>(1);
			if (methodEle.hasAttribute(ROLLBACK_FOR_ATTRIBUTE)) {                                   /* 回滚 */
				String rollbackForValue = methodEle.getAttribute(ROLLBACK_FOR_ATTRIBUTE);
				addRollbackRuleAttributesTo(rollbackRules, rollbackForValue);
			}
			if (methodEle.hasAttribute(NO_ROLLBACK_FOR_ATTRIBUTE)) {
				String noRollbackForValue = methodEle.getAttribute(NO_ROLLBACK_FOR_ATTRIBUTE);
				addNoRollbackRuleAttributesTo(rollbackRules, noRollbackForValue);
			}
			attribute.setRollbackRules(rollbackRules);

			transactionAttributeMap.put(nameHolder, attribute);
		}
		/*
		* 创建一个新的 NameMatchTransactionAttributeSource的 BeanDefinition
		* */
		RootBeanDefinition attributeSourceDefinition = new RootBeanDefinition(NameMatchTransactionAttributeSource.class);
		attributeSourceDefinition.setSource(parserContext.extractSource(attrEle));
		attributeSourceDefinition.getPropertyValues().add("nameMap", transactionAttributeMap);
		/*
		* 这个 BeanDefinition 存放的都是 tx:advice 中 tx:attributes 中的所有的 tx:method
		* */
		return attributeSourceDefinition;
	}

	private void addRollbackRuleAttributesTo(List<RollbackRuleAttribute> rollbackRules, String rollbackForValue) {
		String[] exceptionTypeNames = StringUtils.commaDelimitedListToStringArray(rollbackForValue);
		for (String typeName : exceptionTypeNames) {
			rollbackRules.add(new RollbackRuleAttribute(StringUtils.trimWhitespace(typeName)));
		}
	}

	private void addNoRollbackRuleAttributesTo(List<RollbackRuleAttribute> rollbackRules, String noRollbackForValue) {
		String[] exceptionTypeNames = StringUtils.commaDelimitedListToStringArray(noRollbackForValue);
		for (String typeName : exceptionTypeNames) {
			rollbackRules.add(new NoRollbackRuleAttribute(StringUtils.trimWhitespace(typeName)));
		}
	}

}
