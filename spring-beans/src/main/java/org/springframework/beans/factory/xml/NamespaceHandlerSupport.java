/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.beans.factory.xml;

import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.lang.Nullable;

/**
 * Support class for implementing custom {@link NamespaceHandler NamespaceHandlers}.
 * Parsing and decorating of individual {@link Node Nodes} is done via {@link BeanDefinitionParser}
 * and {@link BeanDefinitionDecorator} strategy interfaces, respectively.
 *
 * <p>Provides the {@link #registerBeanDefinitionParser} and {@link #registerBeanDefinitionDecorator}
 * methods for registering a {@link BeanDefinitionParser} or {@link BeanDefinitionDecorator}
 * to handle a specific element.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @since 2.0
 * @see #registerBeanDefinitionParser(String, BeanDefinitionParser)
 * @see #registerBeanDefinitionDecorator(String, BeanDefinitionDecorator)
 */
public abstract class NamespaceHandlerSupport implements NamespaceHandler {

	/**
	 * Stores the {@link BeanDefinitionParser} implementations keyed by the
	 * local name of the {@link Element Elements} they handle.
	 */
	private final Map<String, BeanDefinitionParser> parsers = new HashMap<>();

	/**
	 * Stores the {@link BeanDefinitionDecorator} implementations keyed by the
	 * local name of the {@link Element Elements} they handle.
	 */
	private final Map<String, BeanDefinitionDecorator> decorators = new HashMap<>();

	/**
	 * Stores the {@link BeanDefinitionDecorator} implementations keyed by the local
	 * name of the {@link Attr Attrs} they handle.
	 */
	private final Map<String, BeanDefinitionDecorator> attributeDecorators = new HashMap<>();


	/**
	 * Parses the supplied {@link Element} by delegating to the {@link BeanDefinitionParser} that is
	 * registered for that {@link Element}.
	 */
	@Override
	@Nullable
	public BeanDefinition parse(Element element, ParserContext parserContext) {
		/*获取元素解析器*/
		/* AOP标签解析 和 context:component-scan 标签解析 */
		/*
		* 1.
		*	 <aop:config>  的解析用  ConfigBeanDefinitionParser
		*	   	                		           	   { -----> MethodLocatingFactoryBean
		*				Advisor --->   adviceDef --->  { -----> expression="execution(Integer com.szu.spring.aopTest.MyCalculator.*(Integer,Integer))"
		*							                   { -----> SimpleBeanFactoryAwareAspectInstanceFactory
		*
		* 	1.1 每个after before after-returning after-throwing around 标签都解析成一个 adviceDef 然后外边再套一层  Advisor
		* 	1.2 五个Advisor（#0---#4），和所有解析出的表达式，放入一个 aspectComponentDefinition 中
		* 	1.3 解析 point-cut ，解析出表达式的结果值
		*   	pointcutsBean 的 BeanDefinition的 beanClass = AspectJExpressionPointcut 把point-cut的BeanDefinition注册到BeanFactory
		*
		*
		* 2.
		* 	 <aop:aspectj-autoproxy>  的解析用 AspectJAutoProxyBeanDefinitionParser 注入 internal
		* 	 										AopNamespaceUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary(parserContext, element);
		*
		* 	 	【AnnotationAwareAspectJAutoProxyCreator 是 BPP】 -> InstantiationAwareBeanPostProcessor -> SmartInstantiationAwareBeanPostProcessor
		*																	用来处理切面的一些注解 @Aspect @Before @After等等
		*
		* 3.
		*    <context:component-scan> 的解析用  ComponentScanBeanDefinitionParser 注入 internal
		*    											AnnotationConfigUtils.registerAnnotationConfigProcessors(readerContext.getRegistry(), source);
		*
		*    		3.1  【ConfigurationClassPostProcessor 是 BFPP】 -> BDRPP : @Component, @Repository, @Service, @Controller, @RestController, @ControllerAdvice
		*    																@Import @ImportResource @ComponentScan @ComponentScans @Bean 的解析
		*
		*    		3.2  【AutowiredAnnotationBeanPostProcessor是 BPP】 -> MergedBeanDefinitionPostProcessor : @Autowired  @Value  @Inject 的解析
		*
		*    		3.3  【CommonAnnotationBeanPostProcessor 是 BPP】 -> InstantiationAwareBeanPostProcessor :  @Resource  @PostConstruct  @PreDestroy
		*
		*
		*
		* 4.
		* 	<tx:advice> 的解析 用的 TxAdviceBeanDefinitionParser
		* 			TransactionInterceptor   ------>   NameMatchTransactionAttributeSource
		*
		*
		*
		* BFPP + BDRPP 在 invokeBeanFactoryPostProcessor()时getBean初始化完毕
		* BPP 在 registerBeanPostProcessor()时的getBean初始化完毕
		* 在最后创建对象的时候就只等着用他们就好了
		* */
		BeanDefinitionParser parser = findParserForElement(element, parserContext);
		return (parser != null ? parser.parse(element, parserContext) : null);
	}

	/**
	 * Locates the {@link BeanDefinitionParser} from the register implementations using
	 * the local name of the supplied {@link Element}.
	 */
	@Nullable
	private BeanDefinitionParser findParserForElement(Element element, ParserContext parserContext) {
		String localName = parserContext.getDelegate().getLocalName(element);
		/*获取到刚才反射得到的解析类*/
		BeanDefinitionParser parser = this.parsers.get(localName);
		if (parser == null) {
			parserContext.getReaderContext().fatal(
					"Cannot locate BeanDefinitionParser for element [" + localName + "]", element);
		}
		return parser;
	}

	/**
	 * Decorates the supplied {@link Node} by delegating to the {@link BeanDefinitionDecorator} that
	 * is registered to handle that {@link Node}.
	 */
	@Override
	@Nullable
	public BeanDefinitionHolder decorate(
			Node node, BeanDefinitionHolder definition, ParserContext parserContext) {

		BeanDefinitionDecorator decorator = findDecoratorForNode(node, parserContext);
		return (decorator != null ? decorator.decorate(node, definition, parserContext) : null);
	}

	/**
	 * Locates the {@link BeanDefinitionParser} from the register implementations using
	 * the local name of the supplied {@link Node}. Supports both {@link Element Elements}
	 * and {@link Attr Attrs}.
	 */
	@Nullable
	private BeanDefinitionDecorator findDecoratorForNode(Node node, ParserContext parserContext) {
		BeanDefinitionDecorator decorator = null;
		String localName = parserContext.getDelegate().getLocalName(node);
		if (node instanceof Element) {
			decorator = this.decorators.get(localName);
		}
		else if (node instanceof Attr) {
			decorator = this.attributeDecorators.get(localName);
		}
		else {
			parserContext.getReaderContext().fatal(
					"Cannot decorate based on Nodes of type [" + node.getClass().getName() + "]", node);
		}
		if (decorator == null) {
			parserContext.getReaderContext().fatal("Cannot locate BeanDefinitionDecorator for " +
					(node instanceof Element ? "element" : "attribute") + " [" + localName + "]", node);
		}
		return decorator;
	}


	/**
	 * Subclasses can call this to register the supplied {@link BeanDefinitionParser} to
	 * handle the specified element. The element name is the local (non-namespace qualified)
	 * name.
	 */
	protected final void registerBeanDefinitionParser(String elementName, BeanDefinitionParser parser) {
		this.parsers.put(elementName, parser);
	}

	/**
	 * Subclasses can call this to register the supplied {@link BeanDefinitionDecorator} to
	 * handle the specified element. The element name is the local (non-namespace qualified)
	 * name.
	 */
	protected final void registerBeanDefinitionDecorator(String elementName, BeanDefinitionDecorator dec) {
		this.decorators.put(elementName, dec);
	}

	/**
	 * Subclasses can call this to register the supplied {@link BeanDefinitionDecorator} to
	 * handle the specified attribute. The attribute name is the local (non-namespace qualified)
	 * name.
	 */
	protected final void registerBeanDefinitionDecoratorForAttribute(String attrName, BeanDefinitionDecorator dec) {
		this.attributeDecorators.put(attrName, dec);
	}

}
