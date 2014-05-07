package liquibase.parser.core.xml;

import liquibase.change.*;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.exception.ChangeLogParseException;
import liquibase.logging.LogFactory;
import liquibase.logging.Logger;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.parser.core.ParsedNode;
import liquibase.precondition.PreconditionFactory;
import liquibase.resource.ResourceAccessor;
import liquibase.sql.visitor.SqlVisitorFactory;
import liquibase.util.StringUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.*;

class XMLChangeLogSAXHandler extends DefaultHandler {

    private final ChangeFactory changeFactory;
    private final PreconditionFactory preconditionFactory;
    private final SqlVisitorFactory sqlVisitorFactory;
    private final ChangeLogParserFactory changeLogParserFactory;

    protected Logger log;

	private final DatabaseChangeLog databaseChangeLog;
	private final ResourceAccessor resourceAccessor;
	private final ChangeLogParameters changeLogParameters;
    private final Stack<ParsedNode> nodeStack = new Stack();
    private Stack<StringBuffer> textStack = new Stack<StringBuffer>();
    private ParsedNode databaseChangeLogTree;


    protected XMLChangeLogSAXHandler(String physicalChangeLogLocation, ResourceAccessor resourceAccessor, ChangeLogParameters changeLogParameters) {
		log = LogFactory.getLogger();
		this.resourceAccessor = resourceAccessor;

		databaseChangeLog = new DatabaseChangeLog();
		databaseChangeLog.setPhysicalFilePath(physicalChangeLogLocation);
		databaseChangeLog.setChangeLogParameters(changeLogParameters);

		this.changeLogParameters = changeLogParameters;

        changeFactory = ChangeFactory.getInstance();
        preconditionFactory = PreconditionFactory.getInstance();
        sqlVisitorFactory = SqlVisitorFactory.getInstance();
        changeLogParserFactory = ChangeLogParserFactory.getInstance();
    }

	public DatabaseChangeLog getDatabaseChangeLog() {
		return databaseChangeLog;
	}

    public ParsedNode getDatabaseChangeLogTree() {
        return databaseChangeLogTree;
    }

    @Override
    public void characters(char ch[], int start, int length) throws SAXException {
        textStack.peek().append(new String(ch, start, length));
    }


    @Override
    public void startElement(String uri, String localName, String qualifiedName, Attributes attributes) throws SAXException {
        ParsedNode node = new ParsedNode(null, localName);
        try {
            if (attributes != null) {
                for (int i=0; i< attributes.getLength(); i++) {
                    node.addChild(null, attributes.getLocalName(i), attributes.getValue(i));
                }
            }
            if (!nodeStack.isEmpty()) {
                nodeStack.peek().addChild(node);
            }
            if (nodeStack.isEmpty()) {
                databaseChangeLogTree = node;
            }
            nodeStack.push(node);
            textStack.push(new StringBuffer());
        } catch (ChangeLogParseException e) {
            throw new SAXException(e);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        ParsedNode node = nodeStack.pop();
        try {
            String seenText = this.textStack.pop().toString();
            if (!StringUtils.trimToEmpty(seenText).equals("")) {
                node.setValue(seenText);
            }
        } catch (ChangeLogParseException e) {
            throw new SAXException(e);
        }
    }

//    public void startElementOld(String uri, String localName, String qName, Attributes baseAttributes) throws SAXException {
//		Attributes atts = new ExpandingAttributes(baseAttributes);
//		try {
//			if ("comment".equals(qName)) {
//				seenText = new StringBuffer();
//			} else if ("validCheckSum".equals(qName)) {
//				seenText = new StringBuffer();
//			} else if ("databaseChangeLog".equals(qName)) {
//				String schemaLocation = atts.getValue("xsi:schemaLocation");
//				if (schemaLocation != null) {
//					Matcher matcher = Pattern.compile(".*dbchangelog-(\\d+\\.\\d+).xsd").matcher(schemaLocation);
//					if (matcher.matches()) {
//						String version = matcher.group(1);
//						if (!version.equals(XMLChangeLogSAXParser
//								.getSchemaVersion())) {
//							log.warning(databaseChangeLog.getPhysicalFilePath()
//									+ " is using schema version " + version
//									+ " rather than version "
//									+ XMLChangeLogSAXParser.getSchemaVersion());
//						}
//					}
//				}
//				databaseChangeLog.setLogicalFilePath(atts.getValue("logicalFilePath"));
//				ObjectQuotingStrategy quotingStrategy = ObjectQuotingStrategy.LEGACY;
//				String quotingStrategyText = atts.getValue("objectQuotingStrategy");
//				if (quotingStrategyText != null) {
//					quotingStrategy = ObjectQuotingStrategy.valueOf(quotingStrategyText);
//				}
//				databaseChangeLog.setObjectQuotingStrategy(quotingStrategy);
//			} else if ("include".equals(qName)) {
//				String fileName = atts.getValue("file");
//				fileName = fileName.replace('\\', '/');
//				boolean isRelativeToChangelogFile = Boolean.parseBoolean(atts.getValue("relativeToChangelogFile"));
//				handleIncludedChangeLog(fileName, isRelativeToChangelogFile, databaseChangeLog.getPhysicalFilePath());
//			} else if ("includeAll".equals(qName)) {
//				String pathName = atts.getValue("path");
//				pathName = pathName.replace('\\', '/');
//
//				if (!(pathName.endsWith("/"))) {
//					pathName = pathName + '/';
//				}
//				log.debug("includeAll for " + pathName);
//				log.debug("Using file opener for includeAll: " + resourceAccessor.toString());
//				boolean isRelativeToChangelogFile = Boolean.parseBoolean(atts.getValue("relativeToChangelogFile"));
//
//                String resourceFilterDef = atts.getValue("resourceFilter");
//                IncludeAllFilter resourceFilter = null;
//                if (resourceFilterDef != null) {
//                    resourceFilter = (IncludeAllFilter) Class.forName(resourceFilterDef).newInstance();
//                }
//                if (isRelativeToChangelogFile) {
//					File changeLogFile = null;
//
//                    Enumeration<URL> resources = resourceAccessor.getResources(databaseChangeLog.getPhysicalFilePath());
//                    while (resources.hasMoreElements()) {
//                        try {
//                            changeLogFile = new File(resources.nextElement().toURI());
//                        } catch (URISyntaxException e) {
//                            continue; //ignore error, probably a URL or something like that
//                        }
//                        if (changeLogFile.exists()) {
//                            break;
//                        } else {
//                            changeLogFile = null;
//                        }
//                    }
//
//                    if (changeLogFile == null) {
//                        throw new SAXException("Cannot determine physical location of "+databaseChangeLog.getPhysicalFilePath());
//                    }
//
//                    File resourceBase = new File(changeLogFile.getParentFile(), pathName);
//
//                    if (!resourceBase.exists()) {
//						throw new SAXException("Resource directory for includeAll does not exist [" + resourceBase.getAbsolutePath() + "]");
//					}
//
//                    pathName = resourceBase.getPath();
//                    pathName = pathName.replaceFirst("^\\Q"+changeLogFile.getParentFile().getAbsolutePath()+"\\E", "");
//                    pathName = databaseChangeLog.getFilePath().replaceFirst("/[^/]*$", "") + pathName;
//					pathName = pathName.replace('\\', '/');
//                    if (!pathName.endsWith("/")) {
//                        pathName = pathName + "/";
//                    }
//
//                    while (pathName.matches(".*/\\.\\./.*")) {
//                        pathName = pathName.replaceFirst("/[^/]+/\\.\\./", "/");
//                    }
//                }
//
//				Enumeration<URL> resourcesEnum = resourceAccessor.getResources(pathName);
//				SortedSet<URL> resources = new TreeSet<URL>(new Comparator<URL>() {
//					@Override
//					public int compare(URL o1, URL o2) {
//						return o1.toString().compareTo(o2.toString());
//					}
//				});
//				while (resourcesEnum.hasMoreElements()) {
//					resources.add(resourcesEnum.nextElement());
//				}
//
//				boolean foundResource = false;
//
//				Set<String> seenPaths = new HashSet<String>();
//				List<String> includedChangeLogs = new LinkedList<String>();
//				for (URL fileUrl : resources) {
//					if (!fileUrl.toExternalForm().startsWith("file:")) {
//						if (fileUrl.toExternalForm().startsWith("jar:file:") || fileUrl.toExternalForm().startsWith("wsjar:file:")
//								|| fileUrl.toExternalForm().startsWith("zip:")) {
//							File zipFileDir = extractZipFile(fileUrl);
//							if (pathName.startsWith("classpath:")) {
//								log.debug("replace classpath");
//								pathName = pathName.replaceFirst("classpath:", "");
//							}
//							if (pathName.startsWith("classpath*:")) {
//								log.debug("replace classpath*");
//								pathName = pathName.replaceFirst("classpath\\*:", "");
//							}
//                            URI fileUri = new File(zipFileDir, pathName).toURI();
//							fileUrl = fileUri.toURL();
//						} else {
//							log.debug(fileUrl.toExternalForm() + " is not a file path");
//							continue;
//						}
//					}
//					File file = new File(fileUrl.toURI());
//					log.debug("includeAll using path " + file.getCanonicalPath());
//					if (!file.exists()) {
//						throw new SAXException("includeAll path " + pathName + " could not be found.  Tried in " + file.toString());
//					}
//					if (file.isDirectory()) {
//						log.debug(file.getCanonicalPath() + " is a directory");
//						for (File childFile : new TreeSet<File>(Arrays.asList(file.listFiles()))) {
//							String path = pathName + childFile.getName();
//							if (!seenPaths.add(path)) {
//								log.debug("already included " + path);
//								continue;
//							}
//
//							includedChangeLogs.add(path);
//						}
//					} else {
//						String path = pathName + file.getName();
//						if (!seenPaths.add(path)) {
//							log.debug("already included " + path);
//							continue;
//						}
//						includedChangeLogs.add(path);
//					}
//				}
//				if (resourceFilter != null) {
//					includedChangeLogs = resourceFilter.filter(includedChangeLogs);
//				}
//
//				for (String path : includedChangeLogs) {
//					if (handleIncludedChangeLog(path, false, databaseChangeLog.getPhysicalFilePath())) {
//						foundResource = true;
//					}
//				}
//
//				if (!foundResource) {
//					throw new SAXException("Could not find directory or directory was empty for includeAll '" + pathName + "'");
//				}
//			} else if (changeSet == null && "changeSet".equals(qName)) {
//				changeSet.setChangeLogParameters(changeLogParameters);
//
//			} else if (changeSet != null && "rollback".equals(qName)) {
//				seenText = new StringBuffer();
//				String id = atts.getValue("changeSetId");
//				if (id != null) {
//					String path = atts.getValue("changeSetPath");
//					if (path == null) {
//						path = databaseChangeLog.getFilePath();
//					}
//					String author = atts.getValue("changeSetAuthor");
//					ChangeSet changeSet = databaseChangeLog.getChangeSet(path, author, id);
//					if (changeSet == null) {
//						throw new SAXException("Could not find changeSet to use for rollback: " + path + ":" + author + ":" + id);
//					} else {
//						for (Change change : changeSet.getChanges()) {
//							this.changeSet.addRollbackChange(change);
//						}
//					}
//				}
//				inRollback = true;
//			} else if ("preConditions".equals(qName)) {
//				rootPrecondition = new PreconditionContainer();
//				rootPrecondition.setOnFail(StringUtils.trimToNull(atts.getValue("onFail")));
//				rootPrecondition.setOnError(StringUtils.trimToNull(atts.getValue("onError")));
//				rootPrecondition.setOnFailMessage(StringUtils.trimToNull(atts.getValue("onFailMessage")));
//				rootPrecondition.setOnErrorMessage(StringUtils.trimToNull(atts.getValue("onErrorMessage")));
//				rootPrecondition.setOnSqlOutput(StringUtils.trimToNull(atts.getValue("onSqlOutput")));
//				preconditionLogicStack.push(rootPrecondition);
//			} else if (currentPrecondition != null && currentPrecondition instanceof CustomPreconditionWrapper && qName.equals("param")) {
//				((CustomPreconditionWrapper) currentPrecondition).setParam(atts.getValue("name"), atts.getValue("value"));
//			} else if (rootPrecondition != null) {
//				currentPrecondition = preconditionFactory.create(localName);
//
//				setAllProperties(currentPrecondition, atts);
//				preconditionLogicStack.peek().addNestedPrecondition(currentPrecondition);
//
//				if (currentPrecondition instanceof PreconditionLogic) {
//					preconditionLogicStack.push(((PreconditionLogic) currentPrecondition));
//				}
//
//				if ("sqlCheck".equals(qName)) {
//					seenText = new StringBuffer();
//				}
//			} else if ("modifySql".equals(qName)) {
//				inModifySql = true;
//				if (StringUtils.trimToNull(atts.getValue("dbms")) != null) {
//					modifySqlDbmsList = new HashSet<String>(StringUtils.splitAndTrim(atts.getValue("dbms"), ","));
//				}
//				if (StringUtils.trimToNull(atts.getValue("context")) != null) {
//					modifySqlContexts = new ContextExpression(atts.getValue("context"));
//				}
//				if (StringUtils.trimToNull(atts.getValue("applyToRollback")) != null) {
//					modifySqlAppliedOnRollback = Boolean.valueOf(atts.getValue("applyToRollback"));
//				}
//			} else if (inModifySql) {
//				SqlVisitor sqlVisitor = sqlVisitorFactory.create(localName);
//				for (int i = 0; i < atts.getLength(); i++) {
//					String attributeName = atts.getLocalName(i);
//					String attributeValue = atts.getValue(i);
//					setProperty(sqlVisitor, attributeName, attributeValue);
//				}
//				sqlVisitor.setApplicableDbms(modifySqlDbmsList);
//				sqlVisitor.setApplyToRollback(modifySqlAppliedOnRollback);
//				sqlVisitor.setContexts(modifySqlContexts);
//
//				changeSet.addSqlVisitor(sqlVisitor);
//			} else if (changeSet != null && change == null) {
//				change = changeFactory.create(localName);
//				if (change == null) {
//					throw new SAXException("Unknown Liquibase extension: " + localName + ".  Are you missing a jar from your classpath?");
//				}
//				change.setChangeSet(changeSet);
//				seenText = new StringBuffer();
//				if (change == null) {
//					throw new MigrationFailedException(changeSet, "Unknown change: " + localName);
//				}
//				change.setResourceAccessor(resourceAccessor);
//				if (change instanceof CustomChangeWrapper) {
//					((CustomChangeWrapper) change).setClassLoader(resourceAccessor.toClassLoader());
//				}
//
//				setAllProperties(change, atts);
//				change.finishInitialization();
//			} else if (change != null && "column".equals(qName)) {
//				ColumnConfig column;
//				if (change instanceof LoadDataChange) {
//					column = new LoadDataColumnConfig();
//				} else if (change instanceof AddColumnChange || change instanceof CreateIndexChange) {
//					column = new AddColumnConfig();
//				} else if (change instanceof CreateIndexChange) {
//					column = new AddColumnConfig();
//				} else {
//					column = new ColumnConfig();
//				}
//				populateColumnFromAttributes(atts, column);
//				if (change instanceof ChangeWithColumns) {
//					((ChangeWithColumns) change).addColumn(column);
//				} else {
//					throw new RuntimeException("Unexpected column tag for " + change.getClass().getName());
//				}
//			} else if (change != null && "whereParams".equals(qName)) {
//				if (!(change instanceof AbstractModifyDataChange)) {
//					throw new RuntimeException("Unexpected change: " + change.getClass().getName());
//				}
//			} else if (change != null && change instanceof AbstractModifyDataChange && "param".equals(qName)) {
//				ColumnConfig param = new ColumnConfig();
//				populateColumnFromAttributes(atts, param);
//				((AbstractModifyDataChange) change).addWhereParam(param);
//			} else if (change != null && "constraints".equals(qName)) {
//				ConstraintsConfig constraints = new ConstraintsConfig();
//				for (int i = 0; i < atts.getLength(); i++) {
//					String attributeName = atts.getLocalName(i);
//					String attributeValue = atts.getValue(i);
//					setProperty(constraints, attributeName, attributeValue);
//				}
//				ColumnConfig lastColumn = null;
//				if (change instanceof ChangeWithColumns) {
//					List<ColumnConfig> columns = ((ChangeWithColumns) change).getColumns();
//					if (columns != null && columns.size() > 0) {
//						lastColumn = columns.get(columns.size() - 1);
//					}
//				} else {
//					throw new RuntimeException("Unexpected change: " + change.getClass().getName());
//				}
//				if (lastColumn == null) {
//					throw new RuntimeException("Could not determine column to add constraint to");
//				}
//				lastColumn.setConstraints(constraints);
//			} else if ("param".equals(qName) && change instanceof CustomChangeWrapper) {
//				if (atts.getValue("value") == null) {
//					paramName = atts.getValue("name");
//					seenText = new StringBuffer();
//				} else {
//					((CustomChangeWrapper) change).setParam(atts.getValue("name"), atts.getValue("value"));
//				}
//			} else if ("where".equals(qName)) {
//				seenText = new StringBuffer();
//			} else if ("property".equals(qName)) {
//				String context = StringUtils.trimToNull(atts.getValue("context"));
//				String dbms = StringUtils.trimToNull(atts.getValue("dbms"));
//				if (StringUtils.trimToNull(atts.getValue("file")) == null) {
//					this.changeLogParameters.set(atts.getValue("name"), atts.getValue("value"), context, dbms);
//				} else {
//					Properties props = new Properties();
//					InputStream propertiesStream = resourceAccessor.getResourceAsStream(atts.getValue("file"));
//					if (propertiesStream == null) {
//						log.info("Could not open properties file " + atts.getValue("file"));
//					} else {
//						props.load(propertiesStream);
//
//						for (Map.Entry entry : props.entrySet()) {
//							this.changeLogParameters.set(entry.getKey().toString(), entry.getValue().toString(), context, dbms);
//						}
//					}
//				}
//			} else if (change instanceof ExecuteShellCommandChange && "arg".equals(qName)) {
//				((ExecuteShellCommandChange) change).addArg(atts.getValue("value"));
//			} else if (change != null) {
//                ChangeParameterMetaData param = ChangeFactory.getInstance().getChangeMetaData(change).getParameters().get(localName);
//                if (param == null || param.getSerializationType() == LiquibaseSerializable.SerializationType.NESTED_OBJECT ) {
//                    String creatorMethod = "create" + localName.substring(0, 1).toUpperCase() + localName.substring(1);
//
//                    Object objectToCreateFrom;
//                    if (changeSubObjects.size() == 0) {
//                        objectToCreateFrom = change;
//                    } else {
//                        objectToCreateFrom = changeSubObjects.peek();
//                    }
//
//                    Method method;
//                    try {
//                        method = objectToCreateFrom.getClass().getMethod(creatorMethod);
//                    } catch (NoSuchMethodException e) {
//                        throw new MigrationFailedException(changeSet, "Could not find creator method " + creatorMethod + " for tag: "
//                                + localName);
//                    }
//                    Object subObject = method.invoke(objectToCreateFrom);
//                    setAllProperties(subObject, atts);
//
//                    changeSubObjects.push(subObject);
//                }
//			} else {
//				throw new MigrationFailedException(changeSet, "Unexpected tag: " + localName);
//			}
//		} catch (Exception e) {
//			log.severe("Error thrown as a SAXException: " + e.getMessage(), e);
//			e.printStackTrace();
//			throw new SAXException(e);
//		}
//	}
//
//	private void setAllProperties(Object object, Attributes atts) throws IllegalAccessException, InvocationTargetException,
//			CustomChangeException {
//		for (int i = 0; i < atts.getLength(); i++) {
//			String attributeName = atts.getQName(i);
//			String attributeValue = atts.getValue(i);
//			setProperty(object, attributeName, attributeValue);
//		}
//	}
//
//	private <T extends ColumnConfig> T getLastColumnConfigFromChange() {
//		T result = null;
//		if (change instanceof ChangeWithColumns) {
//			List<T> columns = ((ChangeWithColumns) change).getColumns();
//			if (columns.size() > 0) {
//				result = columns.get(columns.size() - 1);
//			}
//		} else {
//			throw new RuntimeException("Unexpected change: " + change.getClass().getName());
//		}
//		return result;
//	}
//
//	private void populateColumnFromAttributes(Attributes atts, ColumnConfig column) throws IllegalAccessException,
//			InvocationTargetException, CustomChangeException {
//		for (int i = 0; i < atts.getLength(); i++) {
//			String attributeName = atts.getLocalName(i);
//			String attributeValue = atts.getValue(i);
//			setProperty(column, attributeName, attributeValue);
//		}
//	}
//
//	protected boolean handleIncludedChangeLog(String fileName, boolean isRelativePath, String relativeBaseFileName)
//			throws LiquibaseException {
//
//		if (fileName.equalsIgnoreCase(".svn") || fileName.equalsIgnoreCase("cvs")) {
//			return false;
//		}
//
//		if (isRelativePath) {
//			// workaround for FilenameUtils.normalize() returning null for relative paths like ../conf/liquibase.xml
//			String tempFile = FilenameUtils.concat(FilenameUtils.getFullPath(relativeBaseFileName), fileName);
//			if (tempFile != null && new File(tempFile).exists() == true) {
//				fileName = tempFile;
//			} else {
//				fileName = FilenameUtils.getFullPath(relativeBaseFileName) + fileName;
//			}
//		}
//      DatabaseChangeLog changeLog;
//      try {
//         changeLog= changeLogParserFactory.getParser(fileName, resourceAccessor).parse(fileName, changeLogParameters, resourceAccessor);
//      } catch (UnknownChangelogFormatException e) {
//        log.warning("included file "+relativeBaseFileName + "/" + fileName + " is not a recognized file type");
//                    return false;
//      }
//      PreconditionContainer preconditions = changeLog.getPreconditions();
//		if (preconditions != null) {
//			if (null == databaseChangeLog.getPreconditions()) {
//				databaseChangeLog.setPreconditions(new PreconditionContainer());
//			}
//			databaseChangeLog.getPreconditions().addNestedPrecondition(preconditions);
//		}
//		for (ChangeSet changeSet : changeLog.getChangeSets()) {
//			handleChangeSet(changeSet);
//		}
//
//		return true;
//	}
//
//	private void setProperty(Object object, String attributeName, String attributeValue) throws IllegalAccessException,
//			InvocationTargetException, CustomChangeException {
//		if (object instanceof CustomChangeWrapper) {
//			if (attributeName.equals("class")) {
//				((CustomChangeWrapper) object).setClass(changeLogParameters.expandExpressions(attributeValue));
//			} else {
//				((CustomChangeWrapper) object).setParam(attributeName, changeLogParameters.expandExpressions(attributeValue));
//			}
//		} else {
//			ObjectUtil.setProperty(object, attributeName, changeLogParameters.expandExpressions(attributeValue));
//		}
//	}
//
//	public void endElementOld(String uri, String localName, String qName) throws SAXException {
//		String textString = null;
//		if (seenText != null && seenText.length() > 0) {
//			textString = changeLogParameters.expandExpressions(StringUtils.trimToNull(seenText.toString()));
//		}
//
//		try {
//			if (changeSubObjects.size() > 0) {
//                Object subObject = changeSubObjects.pop();
//                if (textString != null) {
//                    setProperty(subObject, "text", textString);
//                    seenText = null;
//                }
//            } else if (rootPrecondition != null) {
//				if ("preConditions".equals(qName)) {
//					if (changeSet == null) {
//						databaseChangeLog.setPreconditions(rootPrecondition);
//						handlePreCondition(rootPrecondition);
//					} else {
//						changeSet.setPreconditions(rootPrecondition);
//					}
//					rootPrecondition = null;
//				} else if ("and".equals(qName)) {
//					preconditionLogicStack.pop();
//					currentPrecondition = null;
//				} else if ("or".equals(qName)) {
//					preconditionLogicStack.pop();
//					currentPrecondition = null;
//				} else if ("not".equals(qName)) {
//					preconditionLogicStack.pop();
//					currentPrecondition = null;
//				} else if (qName.equals("sqlCheck")) {
//					((SqlPrecondition) currentPrecondition).setSql(textString);
//					currentPrecondition = null;
//				} else if (qName.equals("customPrecondition")) {
//					((CustomPreconditionWrapper) currentPrecondition).setClassLoader(resourceAccessor.toClassLoader());
//					currentPrecondition = null;
//				}
//
//			} else if (changeSet != null && "rollback".equals(qName)) {
//				changeSet.addRollBackSQL(textString);
//				inRollback = false;
//			} else if (change != null && change instanceof RawSQLChange && "comment".equals(qName)) {
//				((RawSQLChange) change).setComment(textString);
//				seenText = new StringBuffer();
//			} else if (change != null && "where".equals(qName)) {
//				if (change instanceof AbstractModifyDataChange) {
//					((AbstractModifyDataChange) change).setWhere(textString);
//				} else {
//					throw new RuntimeException("Unexpected change type: " + change.getClass().getName());
//				}
//				seenText = new StringBuffer();
//			} else if (change != null && change instanceof CreateProcedureChange && "comment".equals(qName)) {
//				((CreateProcedureChange) change).setComments(textString);
//				seenText = new StringBuffer();
//			} else if (change != null && change instanceof CustomChangeWrapper && paramName != null && "param".equals(qName)) {
//				((CustomChangeWrapper) change).setParam(paramName, textString);
//				seenText = new StringBuffer();
//				paramName = null;
//			} else if (changeSet != null && "comment".equals(qName)) {
//				changeSet.setComments(textString);
//				seenText = new StringBuffer();
//			} else if (changeSet != null && "changeSet".equals(qName)) {
//				handleChangeSet(changeSet);
//				changeSet = null;
//			} else if (change != null && qName.equals("column") && textString != null) {
//				if (change instanceof InsertDataChange) {
//					List<ColumnConfig> columns = ((InsertDataChange) change).getColumns();
//					columns.get(columns.size() - 1).setValue(textString);
//				} else if (change instanceof UpdateDataChange) {
//					List<ColumnConfig> columns = ((UpdateDataChange) change).getColumns();
//					columns.get(columns.size() - 1).setValue(textString);
//				} else {
//					throw new RuntimeException("Unexpected column with text: " + textString);
//				}
//				this.seenText = new StringBuffer();
//            } else if (change != null && change instanceof AbstractModifyDataChange && qName.equals("param")
//                        && textString != null) {
//                    List<ColumnConfig> columns = ((AbstractModifyDataChange) change)
//                            .getWhereParams();
//                    columns.get(columns.size() - 1).setValue(textString);
//                    this.seenText = new StringBuffer();
//			} else if (change != null
//					&& localName.equals(changeFactory.getChangeMetaData(change).getName())) {
//				if (textString != null) {
//					if (change instanceof RawSQLChange) {
//						// We've already expanded expressions when we defined 'textString' above. If we enabled
//						// escaping, we cannot re-expand; the now-literal variables in the text would get
//						// incorrectly expanded. If we haven't enabled escaping, then retain the current behavior.
//						String expandedExpression = textString;
//
//						if (!LiquibaseConfiguration.getInstance().getConfiguration(ChangeLogParserCofiguration.class).getSupportPropertyEscaping()) {
//							expandedExpression = changeLogParameters.expandExpressions(textString);
//						}
//						((RawSQLChange) change).setSql(expandedExpression);
//					} else if (change instanceof CreateProcedureChange) {
//						((CreateProcedureChange) change).setProcedureText(textString);
//						// } else if (change instanceof AlterViewChange) {
//						// ((AlterViewChange)
//						// change).setSelectQuery(textString);
//					} else if (change instanceof CreateViewChange) {
//						((CreateViewChange) change).setSelectQuery(textString);
//					} else if (change instanceof StopChange) {
//						((StopChange) change).setMessage(textString);
//					} else {
//                        boolean foundTextParam = false;
//                        for (ChangeParameterMetaData metadata : changeFactory.getChangeMetaData(change).getParameters().values()) {
//                            if (metadata.getSerializationType() == LiquibaseSerializable.SerializationType.DIRECT_VALUE) {
//                                metadata.setValue(change, textString);
//                                foundTextParam = true;
//                                break;
//                            }
//                        }
//
//                        if (!foundTextParam) {
//                            throw new RuntimeException("Unexpected text in " + changeFactory.getChangeMetaData(change).getName());
//                        }
//                    }
//				}
//				seenText = null;
//				if (inRollback) {
//					changeSet.addRollbackChange(change);
//				} else {
//					changeSet.addChange(change);
//				}
//				change = null;
//			} else if (changeSet != null && "validCheckSum".equals(qName)) {
//				changeSet.addValidCheckSum(seenText.toString());
//				seenText = null;
//			} else if ("modifySql".equals(qName)) {
//				inModifySql = false;
//				modifySqlDbmsList = null;
//				modifySqlContexts = null;
//				modifySqlAppliedOnRollback = false;
//			} else if (change != null && textString != null) {
//                setProperty(change, localName, textString);
//            }
//		} catch (Exception e) {
//			log.severe("Error thrown as a SAXException: " + e.getMessage(), e);
//			throw new SAXException(databaseChangeLog.getPhysicalFilePath() + ": " + e.getMessage(), e);
//		}
//	}
//
//	protected void handlePreCondition(@SuppressWarnings("unused") Precondition precondition) {
//		databaseChangeLog.setPreconditions(rootPrecondition);
//	}
//
//	protected void handleChangeSet(ChangeSet changeSet) {
//		databaseChangeLog.addChangeSet(changeSet);
//	}
//
//	/**
//	 * Wrapper for Attributes that expands the value as needed
//	 */
//	private class ExpandingAttributes implements Attributes {
//		private final Attributes attributes;
//
//		private ExpandingAttributes(Attributes attributes) {
//			this.attributes = attributes;
//		}
//
//		@Override
//		public int getLength() {
//			return attributes.getLength();
//		}
//
//		@Override
//		public String getURI(int index) {
//			return attributes.getURI(index);
//		}
//
//		@Override
//		public String getLocalName(int index) {
//			return attributes.getLocalName(index);
//		}
//
//		@Override
//		public String getQName(int index) {
//			return attributes.getQName(index);
//		}
//
//		@Override
//		public String getType(int index) {
//			return attributes.getType(index);
//		}
//
//		@Override
//		public String getValue(int index) {
//			return attributes.getValue(index);
//		}
//
//		@Override
//		public int getIndex(String uri, String localName) {
//			return attributes.getIndex(uri, localName);
//		}
//
//		@Override
//		public int getIndex(String qName) {
//			return attributes.getIndex(qName);
//		}
//
//		@Override
//		public String getType(String uri, String localName) {
//			return attributes.getType(uri, localName);
//		}
//
//		@Override
//		public String getType(String qName) {
//			return attributes.getType(qName);
//		}
//
//		@Override
//		public String getValue(String uri, String localName) {
//			return changeLogParameters.expandExpressions(attributes.getValue(uri, localName));
//		}
//
//		@Override
//		public String getValue(String qName) {
//			return changeLogParameters.expandExpressions(attributes.getValue(qName));
//		}
//	}
//
//	static File extractZipFile(URL resource) throws IOException {
//		String file = resource.getFile();
//		String path = file.split("!")[0];
//		if (path.matches("file:\\/[A-Za-z]:\\/.*")) {
//			path = path.replaceFirst("file:\\/", "");
//		} else {
//			path = path.replaceFirst("file:", "");
//		}
//		path = URLDecoder.decode(path, "UTF-8");
//		File zipfile = new File(path);
//
//		File tempDir = File.createTempFile("liquibase-sax", ".dir");
//		tempDir.delete();
//		tempDir.mkdir();
//
//        JarFile jarFile = new JarFile(zipfile);
//        try {
//            Enumeration<JarEntry> entries = jarFile.entries();
//            while (entries.hasMoreElements()) {
//                JarEntry entry = entries.nextElement();
//                File entryFile = new File(tempDir, entry.getName());
//                entryFile.mkdirs();
//            }
//
//            FileUtil.forceDeleteOnExit(tempDir);
//        } finally {
//            jarFile.close();
//        }
//
//		return tempDir;
//	}
}
