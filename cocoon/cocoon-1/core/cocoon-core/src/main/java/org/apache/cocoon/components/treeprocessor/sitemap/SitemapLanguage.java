/*
 * Copyright 1999-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cocoon.components.treeprocessor.sitemap;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.avalon.excalibur.pool.Recyclable;
import org.apache.avalon.framework.activity.Disposable;
import org.apache.avalon.framework.activity.Initializable;
import org.apache.avalon.framework.configuration.AbstractConfiguration;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.configuration.DefaultConfiguration;
import org.apache.avalon.framework.configuration.SAXConfigurationHandler;
import org.apache.avalon.framework.context.Context;
import org.apache.avalon.framework.context.ContextException;
import org.apache.avalon.framework.context.Contextualizable;
import org.apache.avalon.framework.context.DefaultContext;
import org.apache.avalon.framework.logger.AbstractLogEnabled;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.avalon.framework.service.ServiceManager;
import org.apache.avalon.framework.service.Serviceable;
import org.apache.cocoon.Constants;
import org.apache.cocoon.ProcessingUtil;
import org.apache.cocoon.components.LifecycleHelper;
import org.apache.cocoon.components.source.SourceUtil;
import org.apache.cocoon.components.treeprocessor.AbstractProcessingNode;
import org.apache.cocoon.components.treeprocessor.CategoryNode;
import org.apache.cocoon.components.treeprocessor.CategoryNodeBuilder;
import org.apache.cocoon.components.treeprocessor.ConcreteTreeProcessor;
import org.apache.cocoon.components.treeprocessor.LinkedProcessingNodeBuilder;
import org.apache.cocoon.components.treeprocessor.ParameterizableProcessingNode;
import org.apache.cocoon.components.treeprocessor.ProcessingNode;
import org.apache.cocoon.components.treeprocessor.ProcessingNodeBuilder;
import org.apache.cocoon.components.treeprocessor.ProcessorComponentInfo;
import org.apache.cocoon.components.treeprocessor.NodeBuilderSelector;
import org.apache.cocoon.components.treeprocessor.TreeBuilder;
import org.apache.cocoon.components.treeprocessor.variables.VariableResolver;
import org.apache.cocoon.components.treeprocessor.variables.VariableResolverFactory;
import org.apache.cocoon.configuration.PropertyProvider;
import org.apache.cocoon.configuration.Settings;
import org.apache.cocoon.configuration.impl.MutableSettings;
import org.apache.cocoon.configuration.impl.PropertyHelper;
import org.apache.cocoon.configuration.impl.SettingsHelper;
import org.apache.cocoon.core.container.spring.BeanFactoryFactoryImpl;
import org.apache.cocoon.environment.Environment;
import org.apache.cocoon.environment.internal.EnvironmentHelper;
import org.apache.cocoon.generation.Generator;
import org.apache.cocoon.serialization.Serializer;
import org.apache.cocoon.sitemap.EnterSitemapEventListener;
import org.apache.cocoon.sitemap.LeaveSitemapEventListener;
import org.apache.cocoon.sitemap.PatternException;
import org.apache.cocoon.sitemap.SitemapParameters;
import org.apache.cocoon.util.Deprecation;
import org.apache.cocoon.util.location.Location;
import org.apache.cocoon.util.location.LocationImpl;
import org.apache.cocoon.util.location.LocationUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.excalibur.source.Source;
import org.apache.excalibur.source.SourceResolver;
import org.apache.excalibur.source.TraversableSource;
import org.apache.regexp.RE;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * The tree builder for the sitemap language.
 * 
 * @version $Id$
 */
public class SitemapLanguage
    extends AbstractLogEnabled
    implements TreeBuilder, Contextualizable, Serviceable, Recyclable, BeanFactoryAware {

    private static final String DEFAULT_CONFIG_PROPERTIES = "config/properties";

    private static final String DEFAULT_CONFIG_XCONF  = "config/xconf";

    private static final String DEFAULT_CONFIG_SPRING = "config/spring";

    private static final String CLASSLOADER_CONFIG_NAME = "classloader";

    // Regexp's for splitting expressions
    private static final String COMMA_SPLIT_REGEXP = "[\\s]*,[\\s]*";

    private static final String EQUALS_SPLIT_REGEXP = "[\\s]*=[\\s]*";

    protected Map attributes = new HashMap();

    /** Spring application context. */
    protected ConfigurableListableBeanFactory beanFactory;


    // ----- lifecycle-related objects ------

    /**
     * This component's avalon context
     */
    private Context context;

    /**
     * This component's service manager
     */
    private ServiceManager manager;

    // -------------------------------------

    /**
     * The tree processor that we are building.
     */
    protected ConcreteTreeProcessor processor;

    /**
     * The namespace of configuration for the processor that we are building.
     */
    protected String itsNamespace;

    /**
     * The service manager for the processor that we are building. It is created
     * by {@link #createServiceManager(ClassLoader, Context, Configuration)}.
     */
    private ServiceManager itsManager;

    private ConfigurableListableBeanFactory itsBeanFactory;

    /**
     * Helper object which sets up components in the context of the processor
     * that we are building.
     */
    private LifecycleHelper itsLifecycle;

    /**
     * Selector for ProcessingNodeBuilders which is set up in the context of the
     * processor that we are building.
     */
    private NodeBuilderSelector itsBuilders;

    /**
     * The sitemap component information grabbed while building itsMaanger
     */
    protected ProcessorComponentInfo itsComponentInfo;

    /** Optional event listeners for the enter sitemap event */
    protected List enterSitemapEventListeners = new ArrayList();

    /** Optional event listeners for the leave sitemap event */
    protected List leaveSitemapEventListeners = new ArrayList();

    // -------------------------------------

    /** Nodes gone through setupNode() that implement Initializable */
    private List initializableNodes = new ArrayList();

    /** Nodes gone through setupNode() that implement Disposable */
    private List disposableNodes = new ArrayList();

    /**
     * NodeBuilders created by createNodeBuilder() that implement
     * LinkedProcessingNodeBuilder
     */
    private List linkedBuilders = new ArrayList();

    /** Are we in a state that allows to get registered nodes ? */
    private boolean canGetNode = false;

    /** Nodes registered using registerNode() */
    private Map registeredNodes = new HashMap();

    /** Class Loader for this sitemap. */
    private ClassLoader itsClassLoader = this.getClass().getClassLoader();

    /**
     * @see org.apache.avalon.framework.context.Contextualizable#contextualize(org.apache.avalon.framework.context.Context)
     */
    public void contextualize(Context context) throws ContextException {
        this.context = context;
    }

    /**
     * @see org.apache.avalon.framework.service.Serviceable#service(org.apache.avalon.framework.service.ServiceManager)
     */
    public void service(ServiceManager manager) throws ServiceException {
        this.manager = manager;
    }

    /**
     * Get the location of the treebuilder config file. Can be overridden for
     * other versions.
     * 
     * @return The location of the treebuilder config file
     */
    protected String getBuilderConfigURL() {
        return "resource://org/apache/cocoon/components/treeprocessor/sitemap-language.xml";
    }

    /**
     * @see org.apache.cocoon.components.treeprocessor.TreeBuilder#setAttribute(java.lang.String,
     *      java.lang.Object)
     */
    public void setAttribute(String name, Object value) {
        this.attributes.put(name, value);
    }

    /**
     * @see org.apache.cocoon.components.treeprocessor.TreeBuilder#getAttribute(java.lang.String)
     */
    public Object getAttribute(String name) {
        return this.attributes.get(name);
    }

    /**
     * @see org.apache.cocoon.components.treeprocessor.TreeBuilder#setProcessor(ConcreteTreeProcessor)
     */
    public void setProcessor(ConcreteTreeProcessor processor) {
        this.processor = processor;
    }

    /**
     * @see org.apache.cocoon.components.treeprocessor.TreeBuilder#getProcessor()
     */
    public ConcreteTreeProcessor getProcessor() {
        return this.processor;
    }

    /**
     * @see org.apache.cocoon.components.treeprocessor.TreeBuilder#getBeanFactory()
     */
    public ConfigurableListableBeanFactory getBeanFactory() {
        return this.itsBeanFactory;
    }

    /**
     * @see org.apache.cocoon.components.treeprocessor.TreeBuilder#getClassLoader()
     */
    public ClassLoader getClassLoader() {
        return this.itsClassLoader;
    }

    /**
     * @see org.apache.cocoon.components.treeprocessor.TreeBuilder#getEnterSitemapEventListeners()
     */
    public List getEnterSitemapEventListeners() {
        // we make a copy here, so we can clear(recylce) the list after the
        // sitemap is build
        return (List) ((ArrayList) this.enterSitemapEventListeners).clone();
    }

    /**
     * @see org.apache.cocoon.components.treeprocessor.TreeBuilder#getLeaveSitemapEventListeners()
     */
    public List getLeaveSitemapEventListeners() {
        // we make a copy here, so we can clear(recylce) the list after the
        // sitemap is build
        return (List) ((ArrayList) this.leaveSitemapEventListeners).clone();
    }

    /**
     * @see org.apache.cocoon.components.treeprocessor.TreeBuilder#registerNode(java.lang.String,
     *      org.apache.cocoon.components.treeprocessor.ProcessingNode)
     */
    public boolean registerNode(String name, ProcessingNode node) {
        if (this.registeredNodes.containsKey(name)) {
            return false;
        }
        this.registeredNodes.put(name, node);
        return true;
    }

    /**
     * @see org.apache.cocoon.components.treeprocessor.TreeBuilder#getRegisteredNode(java.lang.String)
     */
    public ProcessingNode getRegisteredNode(String name) {
        if (this.canGetNode) {
            return (ProcessingNode) this.registeredNodes.get(name);
        }
        throw new IllegalArgumentException("Categories are only available during buildNode()");
    }

    /**
     * @see org.apache.cocoon.components.treeprocessor.TreeBuilder#createNodeBuilder(org.apache.avalon.framework.configuration.Configuration)
     */
    public ProcessingNodeBuilder createNodeBuilder(Configuration config) throws Exception {
        // FIXME : check namespace
        String nodeName = config.getName();

        if (getLogger().isDebugEnabled()) {
            getLogger().debug("Creating node builder for " + nodeName);
        }

        ProcessingNodeBuilder builder;
        builder = (ProcessingNodeBuilder) this.itsBuilders.getBuilder(nodeName);

        builder.setBuilder(this);

        if (builder instanceof LinkedProcessingNodeBuilder) {
            this.linkedBuilders.add(builder);
        }

        return builder;
    }

    /**
     * Create the tree once component manager and node builders have been set
     * up. Can be overriden by subclasses to perform pre/post tree creation
     * operations.
     */
    protected ProcessingNode createTree(Configuration tree) throws Exception {
        // Create a node builder from the top-level element
        ProcessingNodeBuilder rootBuilder = createNodeBuilder(tree);

        // Build the whole tree (with an empty buildModel)
        return rootBuilder.buildNode(tree);
    }

    /**
     * Resolve links : call <code>linkNode()</code> on all
     * <code>LinkedProcessingNodeBuilder</code>s. Can be overriden by
     * subclasses to perform pre/post resolution operations.
     *
     * Before linking nodes, lookup the view category node used in
     * {@link #getViewNodes(Collection)}.
     */
    protected void linkNodes() throws Exception {
        // Get the views category node
        this.viewsNode = CategoryNodeBuilder.getCategoryNode(this, "views");

        // Resolve links
        Iterator iter = this.linkedBuilders.iterator();
        while (iter.hasNext()) {
            ((LinkedProcessingNodeBuilder) iter.next()).linkNode();
        }
    }

    /**
     * Get the namespace URI that builders should use to find their nodes.
     */
    public String getNamespace() {
        return this.itsNamespace;
    }

    /**
     * Build a processing tree from a <code>Configuration</code>.
     */
    public ProcessingNode build(Configuration tree) throws Exception {
        // The namespace used in the whole sitemap is the one of the root
        // element
        this.itsNamespace = tree.getNamespace();

        Configuration componentConfig = tree.getChild("components", false);
        Configuration classPathConfig = null;

        if (componentConfig == null) {
            if (getLogger().isDebugEnabled()) {
                getLogger().debug("Sitemap has no components definition at " + tree.getLocation());
            }
        }

        // by default we include configuration files and properties from
        // predefined locations
        boolean useDefaultIncludes = true;
        if ( componentConfig != null ) {
            useDefaultIncludes = componentConfig.getAttributeAsBoolean("use-default-includes", true);
        }

        // Context and manager and classloader for the sitemap we build        
        final Context itsContext = createContext(tree);

        // TODO Get factory from spring
        final BeanFactoryFactoryImpl factory = new BeanFactoryFactoryImpl();
        factory.setBeanFactory(this.beanFactory);

        // compatibility with 2.1.x - check for global variables in sitemap
        // TODO - This will be removed in later versions!
        Properties globalSitemapVariables = null;
        if ( tree.getChild("pipelines").getChild("component-configurations", false) != null ) {
            Deprecation.logger.warn("The 'component-configurations' section in the sitemap is deprecated. Please check for alternatives.");
            globalSitemapVariables = new Properties();
            // now check for global variables - if any other element occurs: throw exception
            Configuration[] children = tree.getChild("pipelines").getChild("component-configurations").getChildren();
            for(int i=0; i<children.length; i++) {
                if ( "global-variables".equals(children[i].getName()) ) {
                    Configuration[] variables = children[i].getChildren();
                    for(int v=0; v<variables.length; v++) {
                        globalSitemapVariables.setProperty(variables[v].getName(), variables[v].getValue());
                    }
               } else {
                    throw new ConfigurationException("Component configurations in the sitemap are not allowed for component: " + children[i].getName());
                }
            }
        }
        // check for sitemap local properties
        Settings settings = (Settings)factory.getCurrentBeanFactory(itsContext).getBean(Settings.ROLE);
        if ( componentConfig != null && componentConfig.getAttribute("property-dir", null) != null ) {
            final String propertyDir = componentConfig.getAttribute("property-dir");
            settings = this.createSettings(settings, propertyDir, useDefaultIncludes, factory.getCurrentBeanFactory(itsContext), globalSitemapVariables);
        } else if ( globalSitemapVariables != null ) {
            MutableSettings s = new MutableSettings(settings);
            PropertyHelper.replaceAll(globalSitemapVariables, settings);
            s.configure(globalSitemapVariables);
            settings = s;
        }
        // replace properties?
        if ( componentConfig == null || componentConfig.getAttributeAsBoolean("replace-properties", true) ) {
            tree = this.replaceProperties(tree, settings);
        }

        // if we want to add the default includes and have no component section
        // we have to create own!
        if ( componentConfig == null && useDefaultIncludes ) {
            componentConfig = new DefaultConfiguration("components",
                                                       tree.getLocation(),
                                                       tree.getNamespace(),
                                                       "");
        }

        if ( componentConfig != null ) {
            // before we pass the configuration we have to strip the
            // additional configuration parts, like classpath as these
            // are not configurations for the component container
            final DefaultConfiguration c = new DefaultConfiguration(componentConfig.getName(), 
                    componentConfig.getLocation(),
                    componentConfig.getNamespace(),
                    "");
            c.addAll(componentConfig);
            classPathConfig = c.getChild(CLASSLOADER_CONFIG_NAME, false);
            if ( classPathConfig != null ) {
                c.removeChild(classPathConfig);
            }
            // and now add default includes
            if ( useDefaultIncludes ) {
                final SourceResolver resolver = this.processor.getSourceResolver();
                Source directory = null;
                try {
                    directory = resolver.resolveURI(DEFAULT_CONFIG_XCONF, null, CONTEXT_PARAMETERS);
                    if (directory.exists() && directory instanceof TraversableSource) {
                        final DefaultConfiguration includeElement = new DefaultConfiguration("include", 
                                                                                             c.getLocation(),
                                                                                             c.getNamespace(),
                                                                                             "");
                        includeElement.setAttribute("dir", DEFAULT_CONFIG_XCONF);
                        includeElement.setAttribute("pattern", "*.xconf");
                        c.addChild(includeElement);
                    }
                } finally {
                    resolver.release(directory);
                    directory = null;
                }
                try {
                    directory = resolver.resolveURI(DEFAULT_CONFIG_SPRING, null, CONTEXT_PARAMETERS);
                    if (directory.exists() && directory instanceof TraversableSource) {
                        final DefaultConfiguration includeElement = new DefaultConfiguration("include-beans", 
                                                                                             c.getLocation(),
                                                                                             c.getNamespace(),
                                                                                             "");
                        includeElement.setAttribute("dir", DEFAULT_CONFIG_SPRING);
                        includeElement.setAttribute("pattern", "*.xml");
                        c.addChild(includeElement);
                    }
                } finally {
                    resolver.release(directory);
                    directory = null;
                }
            }
            componentConfig = c;
        }

        // Create class loader
        this.itsClassLoader = factory.createClassLoader(itsContext, this.processor.getSourceResolver(), classPathConfig);

        // FIXME: Internal configurations doesn't work in a non bean factory
        // environment
        this.itsBeanFactory = factory.createBeanFactory(this.itsClassLoader,
                                                        this.getLogger(),
                                                        componentConfig,
                                                        itsContext,
                                                        this.processor.getSourceResolver(),
                                                        settings);
        this.itsManager = (ServiceManager) this.itsBeanFactory.getBean(ProcessingUtil.SERVICE_MANAGER_ROLE);
        if (componentConfig != null) {
            // only register listeners if a new bean factory is created
            this.registerListeners();
        }
        this.itsComponentInfo = (ProcessorComponentInfo) this.itsManager
                .lookup(ProcessorComponentInfo.ROLE);
        // Create a helper object to setup components
        this.itsLifecycle = new LifecycleHelper(getLogger(), itsContext, this.itsManager, null /* configuration */);

        // Create & initialize the NodeBuilder selector.
        {
            NodeBuilderSelector selector = new NodeBuilderSelector();

            // Load the builder config file
            SourceResolver resolver = (SourceResolver) this.manager.lookup(SourceResolver.ROLE);
            String url = getBuilderConfigURL();
            Configuration config;
            try {
                Source src = resolver.resolveURI(url);
                try {
                    SAXConfigurationHandler handler = new SAXConfigurationHandler();
                    SourceUtil.toSAX(this.manager, src, null, handler);
                    config = handler.getConfiguration();
                } finally {
                    resolver.release(src);
                }
            } catch (Exception e) {
                throw new ConfigurationException("Could not load TreeBuilder configuration from "
                        + url, e);
            } finally {
                this.manager.release(resolver);
            }
            LifecycleHelper.setupComponent(selector, getLogger(), itsContext, this.itsManager,
                    config.getChild("nodes", false), true);
            this.itsBuilders = selector;
        }

        // Calls to getRegisteredNode() are forbidden
        this.canGetNode = false;

        // Collect all disposable variable resolvers
        VariableResolverFactory.setDisposableCollector(this.disposableNodes);

        ProcessingNode result = createTree(tree);

        // Calls to getRegisteredNode() are now allowed
        this.canGetNode = true;

        linkNodes();

        // Initialize all Initializable nodes
        Iterator iter = this.initializableNodes.iterator();
        while (iter.hasNext()) {
            ((Initializable) iter.next()).initialize();
        }

        // And that's all !
        return result;
    }

    /**
     * Replace all properties
     */
    protected Configuration replaceProperties(Configuration tree, Settings settings)
    throws ConfigurationException {
        final DefaultConfiguration root = new DefaultConfiguration(tree, true);
        this.convert(root, settings);
        return tree;
    }

    protected void convert(DefaultConfiguration config, Settings settings)
    throws ConfigurationException {
        final String[] names = config.getAttributeNames();
        for(int i=0; i<names.length; i++) {
            final String value = config.getAttribute(names[i]);
            config.setAttribute(names[i], PropertyHelper.replace(value, settings));
        }
        final String value = config.getValue(null);
        if ( value != null ) {
            config.setValue(PropertyHelper.replace(value, settings));
        }
        final Configuration[] children = config.getChildren();
        for(int m=0; m<children.length; m++) {
            convert((DefaultConfiguration)children[m], settings);
        }
    }
    /**
     * Return the list of <code>ProcessingNodes</code> part of this tree that
     * are <code>Disposable</code>. Care should be taken to properly dispose
     * them before trashing the processing tree.
     */
    public List getDisposableNodes() {
        return this.disposableNodes;
    }

    /**
     * Setup a <code>ProcessingNode</code> by setting its location, calling
     * all the lifecycle interfaces it implements and giving it the parameter
     * map if it's a <code>ParameterizableNode</code>.
     * <p>
     * As a convenience, the node is returned by this method to allow constructs
     * like <code>return treeBuilder.setupNode(new MyNode(), config)</code>.
     */
    public ProcessingNode setupNode(ProcessingNode node, Configuration config) throws Exception {
        Location location = getLocation(config);
        if (node instanceof AbstractProcessingNode) {
            ((AbstractProcessingNode) node).setLocation(location);
            ((AbstractProcessingNode) node).setSitemapExecutor(this.processor.getSitemapExecutor());
        }

        this.itsLifecycle.setupComponent(node, false);

        if (node instanceof ParameterizableProcessingNode) {
            Map params = getParameters(config, location);
            ((ParameterizableProcessingNode) node).setParameters(params);
        }

        if (node instanceof Initializable) {
            this.initializableNodes.add(node);
        }

        if (node instanceof Disposable) {
            this.disposableNodes.add(node);
        }

        return node;
    }

    protected LocationImpl getLocation(Configuration config) {
        String prefix = "";

        if (config instanceof AbstractConfiguration) {
            // FIXME: AbstractConfiguration has a _protected_ getPrefix()
            // method.
            // So make some reasonable guess on the prefix until it becomes
            // public
            String namespace = null;
            try {
                namespace = ((AbstractConfiguration) config).getNamespace();
            } catch (ConfigurationException e) {
                // ignore
            }
            if ("http://apache.org/cocoon/sitemap/1.0".equals(namespace)) {
                prefix = "map";
            }
        }

        StringBuffer desc = new StringBuffer().append('<');
        if (prefix.length() > 0) {
            desc.append(prefix).append(':').append(config.getName());
        } else {
            desc.append(config.getName());
        }
        String type = config.getAttribute("type", null);
        if (type != null) {
            desc.append(" type=\"").append(type).append('"');
        }
        desc.append('>');

        Location rawLoc = LocationUtils.getLocation(config);
        return new LocationImpl(desc.toString(), rawLoc.getURI(), rawLoc.getLineNumber(), rawLoc
                .getColumnNumber());
    }

    /**
     * Get &lt;xxx:parameter&gt; elements as a <code>Map</code> of </code>ListOfMapResolver</code>s,
     * that can be turned into parameters using <code>ListOfMapResolver.buildParameters()</code>.
     * 
     * @return the Map of ListOfMapResolver, or <code>null</code> if there are
     *         no parameters.
     */
    protected Map getParameters(Configuration config, Location location)
            throws ConfigurationException {

        Configuration[] children = config.getChildren("parameter");

        if (children.length == 0) {
            // Parameters are only the component's location
            // TODO Optimize this
            return new SitemapParameters.LocatedHashMap(location, 0);
        }

        Map params = new SitemapParameters.LocatedHashMap(location, children.length + 1);
        for (int i = 0; i < children.length; i++) {
            Configuration child = children[i];
            if (true) { // FIXME : check namespace
                String name = child.getAttribute("name");
                String value = child.getAttribute("value");
                try {
                    params.put(resolve(name), resolve(value));
                } catch (PatternException pe) {
                    String msg = "Invalid pattern '" + value + "' at " + child.getLocation();
                    throw new ConfigurationException(msg, pe);
                }
            }
        }

        return params;
    }

    /**
     * Get the type for a statement : it returns the 'type' attribute if
     * present, and otherwhise the default type defined for this role in the
     * components declarations.
     * 
     * @throws ConfigurationException
     *             if the type could not be found.
     */
    public String getTypeForStatement(Configuration statement, String role)
            throws ConfigurationException {

        // Get the component type for the statement
        String type = statement.getAttribute("type", null);
        if (type == null) {
            type = this.itsComponentInfo.getDefaultType(role);
        }

        if (type == null) {
            throw new ConfigurationException("No default type exists for 'map:"
                    + statement.getName() + "' at " + statement.getLocation());
        }

        final String beanName = role + '/' + type;
        if ( !this.itsBeanFactory.containsBean(beanName) ) {
            throw new ConfigurationException("Type '" + type + "' does not exist for 'map:"
                    + statement.getName() + "' at " + statement.getLocation());
        }

        return type;
    }

    /**
     * Resolve expression using its manager
     */
    protected VariableResolver resolve(String expression) throws PatternException {
        return VariableResolverFactory.getResolver(expression, this.itsManager);
    }

    public void recycle() {
        // Reset all data created during the build
        this.attributes.clear();
        this.canGetNode = false;
        this.disposableNodes = new ArrayList(); // Must not be cleared as it's
                                                // used for processor disposal
        this.initializableNodes.clear();
        this.linkedBuilders.clear();
        this.processor = null; // Set in setProcessor()

        this.itsNamespace = null; // Set in build()
        LifecycleHelper.dispose(this.itsBuilders);
        this.itsBuilders = null; // Set in build()
        this.itsLifecycle = null; // Set in build()
        this.itsManager = null; // Set in build()

        this.registeredNodes.clear();
        this.initializableNodes.clear();
        this.linkedBuilders.clear();
        this.canGetNode = false;
        this.registeredNodes.clear();

        VariableResolverFactory.setDisposableCollector(null);
        this.enterSitemapEventListeners.clear();
        this.leaveSitemapEventListeners.clear();

        // Go back to initial state
        this.labelViews.clear();
        this.viewsNode = null;
        this.isBuildingView = false;
        this.isBuildingErrorHandler = false;
    }

    /**
     * Register all registered sitemap listeners
     */
    protected void registerListeners() {
        String[] names = this.itsBeanFactory.getBeanNamesForType(EnterSitemapEventListener.class);
        for(int i=0; i<names.length; i++) {
            final String beanName = names[i];
            this.enterSitemapEventListeners.add(this.itsBeanFactory.getBean(beanName));
        }
        names = this.itsBeanFactory.getBeanNamesForType(LeaveSitemapEventListener.class);
        for(int i=0; i<names.length; i++) {
            final String beanName = names[i];
            this.leaveSitemapEventListeners.add(this.itsBeanFactory.getBean(beanName));
        }
    }

    /**
     * @see org.apache.cocoon.components.treeprocessor.DefaultTreeBuilder#createContext(org.apache.avalon.framework.configuration.Configuration)
     */
    protected Context createContext(Configuration tree) throws Exception {
        // Create sub-context for this sitemap
        DefaultContext newContext = new DefaultContext(this.context);
        Environment env = EnvironmentHelper.getCurrentEnvironment();
        newContext.put(Constants.CONTEXT_ENV_PREFIX, env.getURIPrefix());

        return newContext;
    }

    // ---- Views management

    /** Collection of view names for each label */
    private Map labelViews = new HashMap();

    /** The views CategoryNode */
    private CategoryNode viewsNode;

    /** Are we currently building a view ? */
    private boolean isBuildingView = false;

    /** Are we currently building a view ? */
    private boolean isBuildingErrorHandler = false;

    /**
     * Pseudo-label for views <code>from-position="first"</code> (i.e.
     * generator).
     */
    public static final String FIRST_POS_LABEL = "!first!";

    /**
     * Pseudo-label for views <code>from-position="last"</code> (i.e.
     * serializer).
     */
    public static final String LAST_POS_LABEL = "!last!";

    /**
     * Set to <code>true</code> while building the internals of a
     * &lt;map:view&gt;
     */
    public void setBuildingView(boolean building) {
        this.isBuildingView = building;
    }

    /**
     * Are we currently building a view ?
     */
    public boolean isBuildingView() {
        return this.isBuildingView;
    }

    /**
     * Set to <code>true</code> while building the internals of a
     * &lt;map:handle-errors&gt;
     */
    public void setBuildingErrorHandler(boolean building) {
        this.isBuildingErrorHandler = building;
    }

    /**
     * Are we currently building an error handler ?
     */
    public boolean isBuildingErrorHandler() {
        return this.isBuildingErrorHandler;
    }

    /**
     * Add a view for a label. This is used to register all views that start
     * from a given label.
     * 
     * @param label
     *            the label (or pseudo-label) for the view
     * @param view
     *            the view name
     */
    public void addViewForLabel(String label, String view) {
        if (getLogger().isDebugEnabled()) {
            getLogger().debug("views:addViewForLabel(" + label + ", " + view + ")");
        }
        Set views = (Set) this.labelViews.get(label);
        if (views == null) {
            views = new HashSet();
            this.labelViews.put(label, views);
        }

        views.add(view);
    }

    /**
     * Get the names of views for a given statement. If the cocoon view exists
     * in the returned collection, the statement can directly branch to the
     * view-handling node.
     * 
     * @param role
     *            the component role (e.g. <code>Generator.ROLE</code>)
     * @param hint
     *            the component hint, i.e. the 'type' attribute
     * @param statement
     *            the sitemap statement
     * @return the view names for this statement
     */
    public Collection getViewsForStatement(String role, String hint, Configuration statement)
            throws Exception {

        String statementLabels = statement.getAttribute("label", null);

        if (this.isBuildingView) {
            // Labels are forbidden inside view definition
            if (statementLabels != null) {
                String msg = "Cannot put a 'label' attribute inside view definition at "
                        + statement.getLocation();
                throw new ConfigurationException(msg);
            }

            // We are currently building a view. Don't recurse !
            return null;
        }

        // Compute the views attached to this component
        Set views = null;

        // Build the set for all labels for this statement
        Set labels = new HashSet();

        // 1 - labels defined on the component
        if (role != null && role.length() > 0) {
            String[] compLabels = this.itsComponentInfo.getLabels(role, hint);
            if (compLabels != null) {
                for (int i = 0; i < compLabels.length; i++) {
                    labels.add(compLabels[i]);
                }
            }
        }

        // 2 - labels defined on this statement
        if (statementLabels != null) {
            labels.addAll(splitLabels(statementLabels));
        }

        // 3 - pseudo-label depending on the role
        if (Generator.ROLE.equals(role)) {
            labels.add("!first!");
        } else if (Serializer.ROLE.equals(role)) {
            labels.add("!last!");
        }

        // Build the set of views attached to these labels
        views = new HashSet();

        // Iterate on all labels for this statement
        Iterator labelIter = labels.iterator();
        while (labelIter.hasNext()) {

            // Iterate on all views for this labek
            Collection coll = (Collection) this.labelViews.get(labelIter.next());
            if (coll != null) {
                Iterator viewIter = coll.iterator();
                while (viewIter.hasNext()) {
                    String viewName = (String) viewIter.next();

                    views.add(viewName);
                }
            }
        }

        // Don't keep empty result
        if (views.size() == 0) {
            views = null;

            if (getLogger().isDebugEnabled()) {
                getLogger().debug(
                        statement.getName() + " has no views at " + statement.getLocation());
            }
        } else {
            if (getLogger().isDebugEnabled()) {
                // Dump matching views
                StringBuffer buf = new StringBuffer(statement.getName() + " will match views [");
                Iterator iter = views.iterator();
                while (iter.hasNext()) {
                    buf.append(iter.next()).append(" ");
                }
                buf.append("] at ").append(statement.getLocation());

                getLogger().debug(buf.toString());
            }
        }

        return views;
    }

    /**
     * Get the {view name, view node} map for a collection of view names. This
     * allows to resolve view nodes at build time, thus avoiding runtime lookup.
     * 
     * @param viewNames
     *            the view names
     * @return association of names to views
     */
    public Map getViewNodes(Collection viewNames) throws Exception {
        if (viewNames == null || viewNames.size() == 0) {
            return null;
        }

        if (this.viewsNode == null) {
            return null;
        }

        Map result = new HashMap();

        Iterator iter = viewNames.iterator();
        while (iter.hasNext()) {
            String viewName = (String) iter.next();
            result.put(viewName, viewsNode.getNodeByName(viewName));
        }

        return result;
    }

    /**
     * Extract pipeline-hints from the given statement (if any exist)
     * 
     * @param role
     *            the component role (e.g. <code>Generator.ROLE</code>)
     * @param hint
     *            the component hint, i.e. the 'type' attribute
     * @param statement
     *            the sitemap statement
     * @return the hint params <code>Map</code> for this statement, or null if
     *         none exist
     */
    public Map getHintsForStatement(String role, String hint, Configuration statement)
            throws Exception {
        // This method implemets the hintParam Syntax as follows:
        // A hints attribute has one or more comma separated hints
        // hints-attr :: hint [ ',' hint ]*
        // A hint is a name and an optional (string) value
        // If there is no value, it is considered as boolean string "true"
        // hint :: literal [ '=' litteral ]
        // literal :: <a character string where the chars ',' and '=' are not
        // permitted>
        //
        // A ConfigurationException is thrown if there is a problem "parsing"
        // the hint.

        String statementHintParams = statement.getAttribute("pipeline-hints", null);
        String componentHintParams = null;
        String hintParams = null;

        // firstly, determine if any pipeline-hints are defined at the component
        // level
        // if so, inherit these pipeline-hints (these hints can be overriden by
        // local pipeline-hints)
        componentHintParams = this.itsComponentInfo.getPipelineHint(role, hint);

        if (componentHintParams != null) {
            hintParams = componentHintParams;

            if (statementHintParams != null) {
                hintParams = hintParams + "," + statementHintParams;
            }
        } else {
            hintParams = statementHintParams;
        }

        // if there are no pipeline-hints defined then
        // it makes no sense to continue so, return null
        if (hintParams == null) {
            return null;
        }

        Map params = new HashMap();

        RE commaSplit = new RE(COMMA_SPLIT_REGEXP);
        RE equalsSplit = new RE(EQUALS_SPLIT_REGEXP);

        String[] expressions = commaSplit.split(hintParams.trim());

        if (getLogger().isDebugEnabled()) {
            getLogger().debug("pipeline-hints: (aggregate-hint) " + hintParams);
        }

        for (int i = 0; i < expressions.length; i++) {
            String[] nameValuePair = equalsSplit.split(expressions[i]);

            try {
                if (nameValuePair.length < 2) {
                    if (getLogger().isDebugEnabled()) {
                        getLogger().debug(
                                "pipeline-hints: (name) " + nameValuePair[0]
                                        + "\npipeline-hints: (value) [implicit] true");
                    }

                    params.put(resolve(nameValuePair[0]), resolve("true"));
                } else {
                    if (getLogger().isDebugEnabled()) {
                        getLogger().debug(
                                "pipeline-hints: (name) " + nameValuePair[0]
                                        + "\npipeline-hints: (value) " + nameValuePair[1]);
                    }

                    params.put(resolve(nameValuePair[0]), resolve(nameValuePair[1]));
                }
            } catch (PatternException pe) {
                String msg = "Invalid pattern '" + hintParams + "' at " + statement.getLocation();
                getLogger().error(msg, pe);
                throw new ConfigurationException(msg, pe);
            }
        }

        return params;
    }

    /**
     * Get the mime-type for a component (either a serializer or a reader)
     *
     * @param role the component role (e.g. <code>Serializer.ROLE</code>)
     * @param hint the component hint, i.e. the 'type' attribute
     * @return the mime-type, or <code>null</code> if none was set
     */
    public String getMimeType(String role, String hint) {
        return this.itsComponentInfo.getMimeType(role, hint);
    }

    /**
     * Split a list of space/comma separated labels into a Collection
     *
     * @return the collection of labels (may be empty, nut never null)
     */
    private static final Collection splitLabels(String labels) {
        if (labels == null) {
            return Collections.EMPTY_SET;
        }
        return Arrays.asList(StringUtils.split(labels, ", \t\n\r"));
    }

    /**
     * @see org.springframework.beans.factory.BeanFactoryAware#setBeanFactory(org.springframework.beans.factory.BeanFactory)
     */
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof ConfigurableListableBeanFactory)) {
            throw new BeanCreationException(
                    "Bean factory for tree processor must be an instance of "
                            + ConfigurableListableBeanFactory.class.getName());
        }
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }

    /**
     * Get the settings for Cocoon.
     * This method reads several property files and merges the result. If there
     * is more than one definition for a property, the last one wins.
     * The property files are read in the following order:
     * 1) PROPERTYDIR/*.properties
     *    Default values for the core and each block - the order in which the files are read is not guaranteed.
     * 2) PROPERTYDIR/[RUNNING_MODE]/*.properties
     *    Default values for the running mode - the order in which the files are read is not guaranteed.
     * 3) Property providers (ToBeDocumented)
     *
     * @return A new Settings object
     */
    protected MutableSettings createSettings(Settings    parent,
                                             String      directory,
                                             boolean     useDefaultIncludes,
                                             BeanFactory parentBeanFactory,
                                             Properties  globalSitemapVariables) {
        // get the running mode
        final String mode = parent.getRunningMode();
        // get properties
        final Properties properties = new Properties();

        // create an empty settings objects
        final MutableSettings s = new MutableSettings(parent);

        // read properties from default includes
        if ( useDefaultIncludes ) {
            this.readProperties(SitemapLanguage.DEFAULT_CONFIG_PROPERTIES, s, properties);
            // read all properties from the mode dependent directory
            this.readProperties(SitemapLanguage.DEFAULT_CONFIG_PROPERTIES + '/' + mode, s, properties);    
        }

        // now read all properties from the properties directory
        this.readProperties(directory, s, properties);
        // read all properties from the mode dependent directory
        this.readProperties(directory + '/' + mode, s, properties);

        // Next look for a custom property provider in the parent bean factory
        if (parentBeanFactory != null && parentBeanFactory.containsBean(PropertyProvider.ROLE) ) {
            try {
                final Environment env = EnvironmentHelper.getCurrentEnvironment();
                final PropertyProvider provider = (PropertyProvider)parentBeanFactory.getBean(PropertyProvider.ROLE);
                // TODO - add the name of the sitemap file to the path
                final Properties providedProperties = provider.getProperties(s, mode, env.getURIPrefix());
                if ( providedProperties != null ) {
                    properties.putAll(providedProperties);
                }
            } catch (Exception ignore) {
                this.getLogger().warn("Unable to get properties from provider.", ignore);
                this.getLogger().warn("Continuing initialization.");            
            }
        }
        if ( globalSitemapVariables != null ) {
            properties.putAll(globalSitemapVariables);
        }
        PropertyHelper.replaceAll(properties, parent);
        s.configure(properties);

        return s;
    }

    /** Parameter map for the context protocol */
    protected static final Map CONTEXT_PARAMETERS = Collections.singletonMap("force-traversable", Boolean.TRUE);

    /**
     * Read all property files from the given directory and apply them to the settings.
     */
    protected void readProperties(String     directoryName,
                                  Settings   s,
                                  Properties properties) {
        final SourceResolver resolver = this.processor.getSourceResolver();
        Source directory = null;
        try {
            directory = resolver.resolveURI(directoryName, null, CONTEXT_PARAMETERS);
            if (directory.exists() && directory instanceof TraversableSource) {
                final List propertyUris = new ArrayList();
                final Iterator c = ((TraversableSource) directory).getChildren().iterator();
                while (c.hasNext()) {
                    final Source src = (Source) c.next();
                    if ( src.getURI().endsWith(".properties") ) {
                        propertyUris.add(src);
                    }
                }
                // sort
                Collections.sort(propertyUris, SettingsHelper.getSourceComparator());
                // now process
                final Iterator i = propertyUris.iterator();
                while ( i.hasNext() ) {
                    final Source src = (Source)i.next();
                    final InputStream propsIS = src.getInputStream();
                    if ( this.getLogger().isDebugEnabled() ) {
                        this.getLogger().debug("Reading settings from '" + src.getURI() + "'.");
                    }
                    properties.load(propsIS);
                    propsIS.close();
                }
            }
        } catch (IOException ignore) {
            this.getLogger().warn("Unable to read from directory " + directoryName, ignore);
            this.getLogger().warn("Continuing initialization.");            
        } finally {
            resolver.release(directory);
        }
    }
}