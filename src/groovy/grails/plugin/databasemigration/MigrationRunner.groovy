/* Copyright 2010-2013 SpringSource.
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
package grails.plugin.databasemigration

import grails.util.GrailsUtil
import groovy.sql.Sql
import liquibase.changelog.ChangeLogIterator
import liquibase.changelog.ChangeSet
import liquibase.changelog.DatabaseChangeLog
import liquibase.changelog.filter.ContextChangeSetFilter
import liquibase.changelog.filter.DbmsChangeSetFilter
import liquibase.changelog.filter.ShouldRunChangeSetFilter
import liquibase.changelog.visitor.ListVisitor
import liquibase.changelog.visitor.UpdateVisitor
import liquibase.exception.LiquibaseException
import liquibase.exception.LockException
import liquibase.lockservice.LockService
import liquibase.parser.ChangeLogParser
import liquibase.parser.ChangeLogParserFactory
import liquibase.util.StringUtils
import java.sql.ResultSet
import liquibase.Liquibase
import liquibase.database.Database

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Based on the class of the same name from Mike Hugo's liquibase-runner plugin.
 *
 * @author Mike Hugo
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class MigrationRunner {

	protected static Logger LOG = LoggerFactory.getLogger(this)

	static void autoRun(migrationCallbacks = null) {
		def dataSourceConfigs = MigrationUtils.getDataSourceConfigs()
		dataSourceConfigs.dataSource = MigrationUtils.application.config.dataSource
		
		for (configAndName in dataSourceConfigs) {
			String dsConfigName = configAndName.key
			ConfigObject configObject = configAndName.value
			
			if (!MigrationUtils.canAutoMigrate(dsConfigName)) {
				LOG.warn "Not running auto migrate for DataSource '$dsConfigName'"
				continue
			}

			def config = MigrationUtils.getConfig(dsConfigName)

			if (!config.updateOnStart) {
				LOG.info "updateOnStart disabled for $dsConfigName; not running migrations"
				continue
			}

			try {
				MigrationUtils.executeInSession(dsConfigName) {
                    def execTime = System.currentTimeMillis()
					Database database
					if(config.multiSchema){
						database = MigrationUtils.getDatabase(null, dsConfigName)
						ResultSet resultSet = database.connection.metaData.schemas
						List schemas = []
						while (resultSet.next()) {
							String schema = resultSet.getString(1)
							if(schema ==~ config.multiSchemaPattern || schema in config.multiSchemaList){ schemas << schema } 
						}

						LOG.info "Found ${schemas.size()} schemas to update"

                        runMigrations(dsConfigName, schemas, config, migrationCallbacks)
					}
					else{
                        runMigrations(dsConfigName,  [ config.updateOnStartDefaultSchema ?: null] , config, migrationCallbacks)
					}
                    LOG.info "Migration '$dsConfigName' ${(System.currentTimeMillis()-execTime)} ms"
				}
			}
			catch (e) {
				GrailsUtil.deepSanitize e
				throw e
			}
		}
	}
	
	static void runMigrations(dsConfigName, schemas, config, migrationCallbacks = null){
        //get changeLog once (skip repeat per schema)
        Database database = MigrationUtils.getDatabase(config.updateOnStartDefaultSchema ?: null, dsConfigName)
        def changeLogs = getChangeLogs(database,config)

        schemas.each { schema ->

            if (config.dropOnStart) {
                LOG.warn "Dropping tables..."
                MigrationUtils.getLiquibase(database).dropAll()
            }

            Map<String, Liquibase> liquibases = [:]
            for (String changelogName in config.updateOnStartFileNames) {
                //change database to use schema
                Liquibase liquibase = MigrationUtils.getLiquibase( MigrationUtils.getDatabase(schema, dsConfigName), changelogName)

                //changeLog parameters must be same as liquibase parameters (connection, schema, others)
                //but needs also parameters from parser (properties from changeLog file)
                DatabaseChangeLog changeLog = changeLogs[changelogName]


                changeLog.changeLogParameters.changeLogParameters.each {
                    if(it.key.equalsIgnoreCase("database.liquibaseSchemaName")){
                        it.value = liquibase.getChangeLogParameters().getValue("database.liquibaseSchemaName")
                    }

                    if(it.key.equalsIgnoreCase("database.defaultSchemaName")){
                        it.value = liquibase.getChangeLogParameters().getValue("database.defaultSchemaName")
                    }
                }
                changeLog.changeLogParameters.currentDatabase = liquibase.changeLogParameters.currentDatabase
                changeLog.changeLogParameters.expressionExpander = liquibase.changeLogParameters.expressionExpander
                changeLog.changeLogParameters.currentContexts = liquibase.changeLogParameters.currentContexts

                if (listUnrunChangeSets(liquibase, config.updateOnStartContexts ?: config.contexts ?: null, changeLog)) {
                    liquibases[changelogName] = liquibase
                }
            }

            if (liquibases) {
                LOG.info "Migrations detected for '$dsConfigName${schema ? '.' + schema : ''}': ${liquibases.keySet()}"
                database = MigrationUtils.getDatabase(schema, dsConfigName)
                try {
                    migrationCallbacks?.beforeStartMigration database
                }
                catch (MissingMethodException ignored) {
                }

                liquibases.each { String changelogName, Liquibase liquibase ->
                    LOG.info "Running script '$changelogName'"

                    try {
                        migrationCallbacks?.onStartMigration database, liquibase, changelogName
                    }
                    catch (MissingMethodException ignored) {
                    }

                    liquibaseUpdate(config.updateOnStartContexts ?: config.contexts ?: null , liquibase , changeLogs[changelogName])
                }

                try {
                    migrationCallbacks?.afterMigrations database
                }
                catch (MissingMethodException ignored) {
                }
            } else {
                LOG.info "No migrations to run for '$dsConfigName${schema ? '.' + schema : ''}'"
            }
        }
	}

    private static Map<String,DatabaseChangeLog> getChangeLogs( database, config){
        def contexts = config.updateOnStartContexts ?: config.contexts ?: null
        def changeLogs=[:]
        for (String changelogName in config.updateOnStartFileNames) {
            Liquibase liquibase = MigrationUtils.getLiquibase(database, changelogName)
            liquibase.changeLogParameters.setContexts(StringUtils.splitAndTrim(contexts, ","));
            ChangeLogParser parser = ChangeLogParserFactory.instance.getParser(liquibase.changeLogFile, liquibase.resourceAccessor)
            DatabaseChangeLog changeLog = parser.parse(liquibase.changeLogFile, liquibase.changeLogParameters, liquibase.resourceAccessor);
            changeLogs << [(changelogName):changeLog]
        }
        return changeLogs
    }

    //liquibase.listUnrunChangeSets will parse migrations. this method skips 'parse'
    private static List<ChangeSet> listUnrunChangeSets(Liquibase liquibase, String contexts, DatabaseChangeLog changeLog) throws LiquibaseException {
        contexts = StringUtils.trimToNull(contexts);
        liquibase.changeLogParameters.setContexts(StringUtils.splitAndTrim(contexts, ","));
        changeLog.validate(liquibase.database, contexts);
        ChangeLogIterator logIterator = liquibase.getStandardChangelogIterator(contexts, changeLog);
        ListVisitor visitor = new ListVisitor();
        logIterator.run(visitor, liquibase.database);
        return visitor.getSeenChangeSets();
    }

    //liquibase.update will parse migrations again. this method skips 'parse'
    private static void liquibaseUpdate( contexts, liquibase, changeLog) throws LiquibaseException {
        contexts = StringUtils.trimToNull(contexts);
        LockService lockService = LockService.getInstance(liquibase.database);
        lockService.waitForLock();

        try {
            liquibase.checkDatabaseChangeLogTable(true, changeLog, contexts);
            ChangeLogIterator changeLogIterator = new ChangeLogIterator(changeLog, new ShouldRunChangeSetFilter(liquibase.database), new ContextChangeSetFilter(contexts), new DbmsChangeSetFilter(liquibase.database));
            changeLogIterator.run(new UpdateVisitor(liquibase.database), liquibase.database);
        } finally {
            try {
                lockService.releaseLock();
            } catch (LockException e) {
                LOG.error("Could not release lock", e);
            }
        }
    }
}
