package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.util.isInterface
import org.cirjson.cirjackson.databind.util.superclass
import kotlin.reflect.KClass

/**
 * Helper class used to encapsulate rules that determine subtypes that are invalid to use, even with default typing,
 * mostly due to security concerns. Used by `BeanDeserializerFactory`
 */
open class SubTypeValidator {

    /**
     * Set of class names of types that are never to be deserialized.
     */
    protected var myConfigIllegalClassNames = DEFAULT_NO_DESERIALIZATION_CLASS_NAMES

    open fun validateSubType(context: DeserializationContext, type: KotlinType, beanDescription: BeanDescription) {
        val raw = type.rawClass
        val fullName = raw.java.name

        if (fullName in myConfigIllegalClassNames) {
            return reportIllegalClass(context, beanDescription, fullName)
        }

        if (raw.isInterface) {
            return
        }

        if (fullName.startsWith(PREFIX_SPRING)) {
            var clazz: KClass<*>? = raw

            while (clazz != null && clazz != Any::class) {
                val name = clazz.simpleName!!

                if ("AbstractPointcutAdvisor" == name || "AbstractApplicationContext" == name) {
                    return reportIllegalClass(context, beanDescription, fullName)
                }

                clazz = clazz.superclass
            }
        } else if (fullName.startsWith(PREFIX_C3P0)) {
            if (fullName.endsWith("DataSource")) {
                return reportIllegalClass(context, beanDescription, fullName)
            }
        }
    }

    protected fun <T> reportIllegalClass(context: DeserializationContext, beanDescription: BeanDescription,
            fullName: String): T {
        return context.reportBadTypeDefinition(beanDescription,
                "Illegal type ($fullName) to deserialize: prevented for security reasons")
    }

    companion object {

        const val PREFIX_SPRING = "org.springframework."

        const val PREFIX_C3P0 = "com.mchange.v2.c3p0."

        /**
         * Set of well-known "nasty classes", deserialization of which is considered dangerous and should (and is)
         * prevented by default.
         */
        val DEFAULT_NO_DESERIALIZATION_CLASS_NAMES = setOf("org.apache.commons.collections.functors.InvokerTransformer",
                "org.apache.commons.collections.functors.InstantiateTransformer",
                "org.apache.commons.collections4.functors.InvokerTransformer",
                "org.apache.commons.collections4.functors.InstantiateTransformer",
                "org.codehaus.groovy.runtime.ConvertedClosure",
                "org.codehaus.groovy.runtime.MethodClosure",
                "org.springframework.beans.factory.ObjectFactory",
                "com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl",
                "org.apache.xalan.xsltc.trax.TemplatesImpl",
                "com.sun.rowset.JdbcRowSetImpl",
                "java.util.logging.FileHandler",
                "java.rmi.server.UnicastRemoteObject",
                "org.springframework.beans.factory.config.PropertyPathFactoryBean",
                "org.springframework.aop.config.MethodLocatingFactoryBean",
                "org.springframework.beans.factory.config.BeanReferenceFactoryBean",

                "org.apache.tomcat.dbcp.dbcp2.BasicDataSource",
                "com.sun.org.apache.bcel.internal.util.ClassLoader",
                "org.hibernate.jmx.StatisticsService",
                "org.apache.ibatis.datasource.jndi.JndiDataSourceFactory",
                "org.apache.ibatis.parsing.XPathParser",

                "jodd.db.connection.DataSourceConnectionProvider",

                "oracle.jdbc.connector.OracleManagedConnectionFactory",
                "oracle.jdbc.rowset.OracleJDBCRowSet",

                "org.slf4j.ext.EventData",
                "flex.messaging.util.concurrent.AsynchBeansWorkManagerExecutor",
                "com.sun.deploy.security.ruleset.DRSHelper",
                "org.apache.axis2.jaxws.spi.handler.HandlerResolverImpl",

                "org.jboss.util.propertyeditor.DocumentEditor",
                "org.apache.openjpa.ee.RegistryManagedRuntime",
                "org.apache.openjpa.ee.JNDIManagedRuntime",
                "org.apache.openjpa.ee.WASRegistryManagedRuntime",
                "org.apache.axis2.transport.jms.JMSOutTransportInfo",

                "com.mysql.cj.jdbc.admin.MiniAdmin",

                "ch.qos.logback.core.db.DriverManagerConnectionSource",

                "org.jdom.transform.XSLTransformer",
                "org.jdom2.transform.XSLTransformer",

                "net.sf.ehcache.transaction.manager.DefaultTransactionManagerLookup",
                "net.sf.ehcache.hibernate.EhcacheJtaTransactionManagerLookup",

                "ch.qos.logback.core.db.JNDIConnectionSource",

                "com.zaxxer.hikari.HikariConfig",
                "com.zaxxer.hikari.HikariDataSource",

                "org.apache.cxf.jaxrs.provider.XSLTJaxbProvider",

                "org.apache.commons.configuration.JNDIConfiguration",
                "org.apache.commons.configuration2.JNDIConfiguration",

                "org.apache.xalan.lib.sql.JNDIConnectionPool",
                "com.sun.org.apache.xalan.internal.lib.sql.JNDIConnectionPool",

                "org.apache.commons.dbcp.cpdsadapter.DriverAdapterCPDS",
                "org.apache.commons.dbcp.datasources.PerUserPoolDataSource",
                "org.apache.commons.dbcp.datasources.SharedPoolDataSource",
                "com.p6spy.engine.spy.P6DataSource",

                "org.apache.log4j.receivers.db.DriverManagerConnectionSource",
                "org.apache.log4j.receivers.db.JNDIConnectionSource",

                "net.sf.ehcache.transaction.manager.selector.GenericJndiSelector",
                "net.sf.ehcache.transaction.manager.selector.GlassfishSelector",

                "org.apache.xbean.propertyeditor.JndiConverter",

                "org.apache.hadoop.shaded.com.zaxxer.hikari.HikariConfig",

                "com.ibatis.sqlmap.engine.transaction.jta.JtaTransactionConfig",
                "br.com.anteros.dbcp.AnterosDBCPConfig",
                "br.com.anteros.dbcp.AnterosDBCPDataSource",

                "javax.swing.JEditorPane",
                "javax.swing.JTextPane",

                "org.apache.shiro.realm.jndi.JndiRealmFactory",
                "org.apache.shiro.jndi.JndiObjectFactory",

                "org.apache.ignite.cache.jta.jndi.CacheJndiTmLookup",
                "org.apache.ignite.cache.jta.jndi.CacheJndiTmFactory",
                "org.quartz.utils.JNDIConnectionProvider",

                "org.apache.aries.transaction.jms.internal.XaPooledConnectionFactory",
                "org.apache.aries.transaction.jms.RecoverablePooledConnectionFactory",

                "com.caucho.config.types.ResourceRef",

                "org.aoju.bus.proxy.provider.RmiProvider",
                "org.aoju.bus.proxy.provider.remoting.RmiProvider",

                "org.apache.activemq.ActiveMQConnectionFactory",
                "org.apache.activemq.ActiveMQXAConnectionFactory",
                "org.apache.activemq.spring.ActiveMQConnectionFactory",
                "org.apache.activemq.spring.ActiveMQXAConnectionFactory",
                "org.apache.activemq.pool.JcaPooledConnectionFactory",
                "org.apache.activemq.pool.PooledConnectionFactory",
                "org.apache.activemq.pool.XaPooledConnectionFactory",
                "org.apache.activemq.jms.pool.XaPooledConnectionFactory",
                "org.apache.activemq.jms.pool.JcaPooledConnectionFactory",

                "org.apache.commons.proxy.provider.remoting.RmiProvider",

                "org.apache.commons.jelly.impl.Embedded",

                "oadd.org.apache.xalan.lib.sql.JNDIConnectionPool",
                "oadd.org.apache.commons.dbcp.cpdsadapter.DriverAdapterCPDS",
                "oadd.org.apache.commons.dbcp.datasources.PerUserPoolDataSource",
                "oadd.org.apache.commons.dbcp.datasources.SharedPoolDataSource",

                "oracle.jms.AQjmsQueueConnectionFactory",
                "oracle.jms.AQjmsXATopicConnectionFactory",
                "oracle.jms.AQjmsTopicConnectionFactory",
                "oracle.jms.AQjmsXAQueueConnectionFactory",
                "oracle.jms.AQjmsXAConnectionFactory",

                "org.jsecurity.realm.jndi.JndiRealmFactory",

                "com.pastdev.httpcomponents.configuration.JndiConfiguration",

                "com.nqadmin.rowset.JdbcRowSetImpl",
                "org.arrah.framework.rdbms.UpdatableJdbcRowsetImpl",

                "org.apache.commons.dbcp2.datasources.PerUserPoolDataSource",
                "org.apache.commons.dbcp2.datasources.SharedPoolDataSource",
                "org.apache.commons.dbcp2.cpdsadapter.DriverAdapterCPDS",

                "com.newrelic.agent.deps.ch.qos.logback.core.db.JNDIConnectionSource",
                "com.newrelic.agent.deps.ch.qos.logback.core.db.DriverManagerConnectionSource",

                "org.apache.tomcat.dbcp.dbcp.cpdsadapter.DriverAdapterCPDS",
                "org.apache.tomcat.dbcp.dbcp.datasources.PerUserPoolDataSource",
                "org.apache.tomcat.dbcp.dbcp.datasources.SharedPoolDataSource",

                "org.apache.tomcat.dbcp.dbcp2.cpdsadapter.DriverAdapterCPDS",
                "org.apache.tomcat.dbcp.dbcp2.datasources.PerUserPoolDataSource",
                "org.apache.tomcat.dbcp.dbcp2.datasources.SharedPoolDataSource",

                "com.oracle.wls.shaded.org.apache.xalan.lib.sql.JNDIConnectionPool",

                "org.docx4j.org.apache.xalan.lib.sql.JNDIConnectionPool")

        val INSTANCE = SubTypeValidator()

    }

}