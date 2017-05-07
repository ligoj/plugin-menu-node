package org.ligoj.app.plugin.menu.node;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.transaction.Transactional;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ligoj.app.AbstractAppTest;
import org.ligoj.app.api.NodeVo;
import org.ligoj.app.model.Node;
import org.ligoj.app.plugin.id.resource.CompanyResource;
import org.ligoj.app.resource.node.NodeResource;
import org.ligoj.bootstrap.core.SpringUtils;
import org.ligoj.bootstrap.model.system.SystemConfiguration;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.ligoj.bootstrap.resource.system.session.SessionSettings;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import net.sf.ehcache.CacheManager;

/**
 * Test of {@link ToolSessionSettingsProvider}
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Transactional
@Rollback
public class ToolSessionSettingsProviderTest extends AbstractAppTest {

	@Before
	public void prepareData() throws IOException {
		// Only with Spring context
		persistEntities("csv", new Class[] { SystemConfiguration.class, Node.class }, StandardCharsets.UTF_8.name());
		CacheManager.getInstance().getCache("configuration").removeAll();

		// For the cache to be created
		getUser().findAll();
	}

	@Autowired
	private ToolSessionSettingsProvider provider;

	@Autowired
	private ConfigurationResource configuration;

	@SuppressWarnings("unchecked")
	@Before
	public void mockApplicationContext() {
		final ApplicationContext applicationContext = Mockito.mock(ApplicationContext.class,
				AdditionalAnswers.delegatesTo(super.applicationContext));
		SpringUtils.setSharedApplicationContext(applicationContext);
		Mockito.doAnswer(invocation -> {
			final Class<?> requiredType = (Class<Object>) invocation.getArguments()[0];
			if (requiredType == SessionSettings.class) {
				return new SessionSettings();
			}
			return ToolSessionSettingsProviderTest.super.applicationContext.getBean(requiredType);
		}).when(applicationContext).getBean(ArgumentMatchers.any(Class.class));
	}

	@Test
	public void decorate() {
		initSpringSecurityContext("fdaugan");
		final SessionSettings details = new SessionSettings();
		details.setUserSettings(new HashMap<>());
		final ToolSessionSettingsProvider provider = new ToolSessionSettingsProvider();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(provider);
		provider.companyResource = Mockito.mock(CompanyResource.class);
		provider.nodeResource = Mockito.mock(NodeResource.class);
		final NodeVo node = new NodeVo();
		node.setId("service:km:confluence:dig");
		Mockito.when(provider.nodeResource.findAll())
				.thenReturn(Collections.singletonMap("service:km:confluence:dig", node));
		Mockito.when(provider.companyResource.isUserInternalCommpany()).thenReturn(true);
		provider.decorate(details);
		Assert.assertEquals(Boolean.TRUE, details.getUserSettings().get("internal"));
		@SuppressWarnings({ "unchecked", "rawtypes" })
		final List<Map<String, Object>> globalTools = (List) details.getUserSettings().get("globalTools");
		Assert.assertEquals(1, globalTools.size());
		Assert.assertEquals("service:km:confluence:dig", ((NodeVo) globalTools.get(0).get("node")).getId());
	}

	/**
	 * Invalid JSon in tool configuration.
	 */
	@Test
	public void decorateError() {
		initSpringSecurityContext("fdaugan");
		final SessionSettings details = new SessionSettings();
		details.setUserSettings(new HashMap<>());
		final ToolSessionSettingsProvider resource = new ToolSessionSettingsProvider();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		configuration.saveOrUpdate("global.tools.internal", "{error}");
		resource.decorate(details);
		Assert.assertNull(details.getUserSettings().get("globalTools"));
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void decorateExternal() {
		initSpringSecurityContext("wuser");
		final SessionSettings details = new SessionSettings();
		details.setUserSettings(new HashMap<>());
		final ToolSessionSettingsProvider provider = new ToolSessionSettingsProvider();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(provider);
		provider.companyResource = Mockito.mock(CompanyResource.class);
		Mockito.when(provider.companyResource.isUserInternalCommpany()).thenReturn(false);
		provider.decorate(details);
		Assert.assertEquals(Boolean.TRUE, details.getUserSettings().get("external"));
		Assert.assertTrue(((Collection) details.getUserSettings().get("globalTools")).isEmpty());
	}

	@Test
	public void getKey() {
		Assert.assertEquals("feature:menu:node", provider.getKey());
	}

	@Test
	public void getInstalledEntities() {
		Assert.assertTrue(provider.getInstalledEntities().contains(SystemConfiguration.class));
	}
}
