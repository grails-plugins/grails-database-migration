/* Copyright 2010-2011 SpringSource.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import java.sql.SQLException

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class DbmUpdateTests extends AbstractScriptTests {

    @Override
    protected void tearDown() {
        super.tearDown()
        new File('target/created_in_check').delete()
        new File('target/created_in_create').delete()
        new File('target/created_in_init').delete()
        new File('target/created_in_rollback').delete()
    }

    void testUpdate() {

		assertTableCount 1

		// should fail since the table isn't there yet
		String message = shouldFail(SQLException) {
			executeUpdate AbstractScriptTests.URL, '''
				insert into person(version, name, street1, street2, zipcode)
				values (0, 'test.name', 'test.street1', 'test.street2', '12345')
			'''
		}
		assertTrue message.contains('Table "PERSON" not found')

		copyTestChangelog()

		executeAndCheck 'dbm-update'

		// original + 2 Liquibase + new person table
		assertTableCount 4

		assertTrue output.contains(
			'Starting dbm-update for database sa @ jdbc:h2:tcp://localhost/./target/testdb/testdb')

		// now we should be able to insert into person table
		executeUpdate AbstractScriptTests.URL, '''
			insert into person(version, name, street1, street2, zipcode)
			values (0, 'test.name', 'test.street1', 'test.street2', '12345')
		'''
	}

	void testUpdateWithGroovyChanges() {

		assertTableCount 1

		// should fail since the table isn't there yet
        def url = AbstractScriptTests.URL
        String message = shouldFail(SQLException) {
			executeUpdate url, "insert into grails(name, foo, bar) values ('test.name', 1000, 5000)"
		}
		assertTrue message.contains('Table "GRAILS" not found')

		copyTestChangelog 'test.custom.changelog'

		def initFile = new File('target/created_in_init')
		initFile.deleteOnExit()
		assertFalse initFile.exists()

		def createFile = new File('target/created_in_create')
		createFile.deleteOnExit()
		assertFalse createFile.exists()

		def rollbackFile = new File('target/created_in_rollback')
		rollbackFile.deleteOnExit()
		assertFalse rollbackFile.exists()

		executeAndCheck 'dbm-update'

		assertTrue output.contains("Keep out of reach of children")

		// original + 2 Liquibase + new 'grails' table
		assertTableCount 4

		assertTrue initFile.exists()
		String initFileContent = initFile.text
		assertTrue initFileContent.contains("in init")

		assertTrue createFile.exists()
		String createFileContent = createFile.text
		assertTrue createFileContent.contains("in create")
		assertTrue createFileContent.contains("database class: 'liquibase.database.core.H2Database', typeName: 'h2'")
		assertTrue createFileContent.contains("databaseConnection class: 'liquibase.database.jvm.JdbcConnection'")
		assertTrue createFileContent.contains("connection class: '\$Proxy")

		assertFalse rollbackFile.exists()

		def tableSize = { newSql().firstRow('select count(*) from grails')[0] }

		assertEquals 20, tableSize()

		// now we should be able to insert into grails table
		executeUpdate url, "insert into grails(name, foo, bar) values ('test.name', 1000, 5000)"
		assertEquals 21, tableSize()

		// roll back the 2nd change
		executeAndCheck(['dbm-rollback-count', '1'])

		assertTrue rollbackFile.exists()
		String rollbackFileContent = rollbackFile.text
		assertTrue rollbackFileContent.contains("in rollback")
		assertTrue rollbackFileContent.contains("database class: 'liquibase.database.core.H2Database', typeName: 'h2'")
		assertTrue rollbackFileContent.contains("databaseConnection class: 'liquibase.database.jvm.JdbcConnection'")
		assertTrue rollbackFileContent.contains("connection class: '\$Proxy")

		// should only have the row we inserted above
		assertEquals 1, tableSize()

		// should fail since the column was dropped
		message = shouldFail(SQLException) {
			executeUpdate url, "insert into grails(name, foo, bar) values ('test.name.2', 2000, 5100)"
		}
		assertTrue message.contains('Column "BAR" not found')

		executeUpdate url, "insert into grails(name, foo) values ('test.name.2', 2000)"
		assertEquals 2, tableSize()
	}

	void testUpdateWithGroovyPreconditionOk() {

		assertTableCount 1

		copyTestChangelog 'test.customprecondition.changelog'

		def checkFile = new File('target/created_in_check')
		checkFile.deleteOnExit()
		assertFalse checkFile.exists()

		def dataFile = new File('target/check_data')
		dataFile.deleteOnExit()
		dataFile.delete()
		assertFalse dataFile.exists()
		dataFile.withWriter { it.write '5' }

		executeAndCheck 'dbm-update'

		assertTrue output.contains('ChangeSet changelog.cli.test.groovy::create_grails_table::burt ran successfully')

		// original + 2 Liquibase + new 'grails' table
		assertTableCount 4
		assertTrue findAllTableNames().contains('precondition_test')

		assertTrue checkFile.exists()
		String checkFileContent = checkFile.text
		assertTrue checkFileContent.contains('database null: false')
		assertTrue checkFileContent.contains('changeLog null: false')
		assertTrue checkFileContent.contains('changeSet null: false')
		assertTrue checkFileContent.contains('ctx null: false')
		assertTrue checkFileContent.contains('resourceAccessor null: false')
	}

	void testUpdateWithGroovyPreconditionFail() {

		assertTableCount 1

		copyTestChangelog 'test.customprecondition.changelog'

		def dataFile = new File('target/check_data')
		dataFile.deleteOnExit()
		dataFile.delete()
		assertFalse dataFile.exists()
		dataFile.withWriter { it.write '15' }

		executeAndCheck(['dbm-update'], false)

		assertTrue output.contains('Migration failed for change set changelog.cli.test.groovy::create_grails_table::burt:')
		assertTrue output.contains('changelog.cli.test.groovy : value cannot be larger than 10')
		assertTrue output.contains('Caused By: Preconditions Failed')

		// original + 2 Liquibase but no new table from changelog
		assertTableCount 3
	}

	void testUpdateWithGroovyPreconditionFailAssert() {

		assertTableCount 1

		copyTestChangelog 'test.customprecondition.changelog'

		def dataFile = new File('target/check_data')
		dataFile.deleteOnExit()
		dataFile.delete()
		assertFalse dataFile.exists()
		dataFile.withWriter { it.write '42' }

		executeAndCheck(['dbm-update'], false)

		assertTrue output.contains('Migration failed for change set changelog.cli.test.groovy::create_grails_table::burt:')
		assertTrue output.contains('changelog.cli.test.groovy : assert testValue != 42')
		assertTrue output.contains('Caused By: Preconditions Failed')

		// original + 2 Liquibase but no new table from changelog
		assertTableCount 3
	}

	void testUpdateWithGroovyPreconditionFailAssertWithMessage() {

		assertTableCount 1

		copyTestChangelog 'test.customprecondition.changelog'

		def dataFile = new File('target/check_data')
		dataFile.deleteOnExit()
		dataFile.delete()
		assertFalse dataFile.exists()
		dataFile.withWriter { it.write '237' }

		executeAndCheck(['dbm-update'], false)

		assertTrue output.contains('Migration failed for change set changelog.cli.test.groovy::create_grails_table::burt:')
		assertTrue output.contains('changelog.cli.test.groovy : value cannot be 237. Expression: (testValue != 237). Values: testValue = 237')
		assertTrue output.contains('Caused By: Preconditions Failed')

		// original + 2 Liquibase but no new table from changelog
		assertTableCount 3
	}

	void testUpdateWithGroovyPreconditionUnhandledException() {

		assertTableCount 1

		copyTestChangelog 'test.customprecondition.changelog'

		def dataFile = new File('target/check_data')
		dataFile.deleteOnExit()
		dataFile.delete()
		assertFalse dataFile.exists()
		dataFile.withWriter { it.write '-1' }

		executeAndCheck(['dbm-update'], false)

		assertTrue output.contains('Migration failed for change set changelog.cli.test.groovy::create_grails_table::burt:')
		assertTrue output.contains('changelog.cli.test.groovy : unhandled runtime exception')
		assertTrue output.contains('Caused By: Precondition Error')

		// original + 2 Liquibase but no new table from changelog
		assertTableCount 3
	}

    void testUpdateForSecondaryDataSource() {

        def secondary_url = AbstractScriptTests.SECONDARY_URL

   		assertTableCount 1, secondary_url

   		// should fail since the table isn't there yet
   		String message = shouldFail(SQLException) {
   			executeUpdate secondary_url, '''
   				insert into person(version, name, street1, street2, zipcode)
   				values (0, 'test.name', 'test.street1', 'test.street2', '12345')
   			'''
   		}
   		assertTrue message.contains('Table "PERSON" not found')

   		copyTestChangelog('test.changelog', AbstractScriptTests.SECONDARY_TEST_CHANGELOG)

   		executeAndCheck (['dbm-update', "--dataSource=secondary"])

   		// original + 2 Liquibase + new person table
   		assertTableCount 4, secondary_url

   		assertTrue output.contains(
   			'Starting dbm-update for database sa @ jdbc:h2:tcp://localhost/./target/testdb/testdb-secondary')

   		// now we should be able to insert into person table
   		executeUpdate secondary_url, '''
   			insert into person(version, name, street1, street2, zipcode)
   			values (0, 'test.name', 'test.street1', 'test.street2', '12345')
   		'''
   	}

    void testUpdateWithGroovyChangesForSecondaryDataSource() {

        def secondary_url = AbstractScriptTests.SECONDARY_URL

        assertTableCount 1, secondary_url

   		// should fail since the table isn't there yet
           String message = shouldFail(SQLException) {
   			executeUpdate secondary_url, "insert into grails(name, foo, bar) values ('test.name', 1000, 5000)"
   		}
   		assertTrue message.contains('Table "GRAILS" not found')

   		copyTestChangelog 'test.custom.changelog', SECONDARY_TEST_CHANGELOG

   		def initFile = new File('target/created_in_init')
   		initFile.deleteOnExit()
   		assertFalse initFile.exists()

   		def createFile = new File('target/created_in_create')
   		createFile.deleteOnExit()
   		assertFalse createFile.exists()

   		def rollbackFile = new File('target/created_in_rollback')
   		rollbackFile.deleteOnExit()
   		assertFalse rollbackFile.exists()

   		executeAndCheck (['dbm-update', '--dataSource=secondary'])

   		assertTrue output.contains("Keep out of reach of children")

   		// original + 2 Liquibase + new 'grails' table
   		assertTableCount 4, SECONDARY_URL

   		assertTrue initFile.exists()
   		String initFileContent = initFile.text
   		assertTrue initFileContent.contains("in init")

   		assertTrue createFile.exists()
   		String createFileContent = createFile.text
   		assertTrue createFileContent.contains("in create")
   		assertTrue createFileContent.contains("database class: 'liquibase.database.core.H2Database', typeName: 'h2'")
   		assertTrue createFileContent.contains("databaseConnection class: 'liquibase.database.jvm.JdbcConnection'")
   		assertTrue createFileContent.contains("connection class: '\$Proxy")

   		assertFalse rollbackFile.exists()

   		def tableSize = { newSql(secondary_url).firstRow('select count(*) from grails')[0] }

   		assertEquals 20, tableSize()

   		// now we should be able to insert into grails table
   		executeUpdate secondary_url, "insert into grails(name, foo, bar) values ('test.name', 1000, 5000)"
   		assertEquals 21, tableSize()

   		// roll back the 2nd change
   		executeAndCheck(['dbm-rollback-count', '1', '--dataSource=secondary'])

   		assertTrue rollbackFile.exists()
   		String rollbackFileContent = rollbackFile.text
   		assertTrue rollbackFileContent.contains("in rollback")
   		assertTrue rollbackFileContent.contains("database class: 'liquibase.database.core.H2Database', typeName: 'h2'")
   		assertTrue rollbackFileContent.contains("databaseConnection class: 'liquibase.database.jvm.JdbcConnection'")
   		assertTrue rollbackFileContent.contains("connection class: '\$Proxy")

   		// should only have the row we inserted above
   		assertEquals 1, tableSize()

   		// should fail since the column was dropped
   		message = shouldFail(SQLException) {
   			executeUpdate secondary_url, "insert into grails(name, foo, bar) values ('test.name.2', 2000, 5100)"
   		}
   		assertTrue message.contains('Column "BAR" not found')

   		executeUpdate secondary_url, "insert into grails(name, foo) values ('test.name.2', 2000)"
   		assertEquals 2, tableSize()
   	}


    void testUpdateWithGroovyPreconditionOkForSecondaryDataSource() {

   		assertTableCount 1, SECONDARY_URL

   		copyTestChangelog 'test.customprecondition.changelog', SECONDARY_TEST_CHANGELOG

   		def checkFile = new File('target/created_in_check')
   		checkFile.deleteOnExit()
   		assertFalse checkFile.exists()

   		def dataFile = new File('target/check_data')
   		dataFile.deleteOnExit()
   		dataFile.delete()
   		assertFalse dataFile.exists()
   		dataFile.withWriter { it.write '5' }

   		executeAndCheck (['dbm-update', '--dataSource=secondary'])

        println output

   		assertTrue output.contains('ChangeSet changelog.cli.secondary-test.groovy::create_grails_table::burt ran successfully')

   		// original + 2 Liquibase + new 'grails' table
   		assertTableCount 4, SECONDARY_URL
   		assertTrue findAllTableNames(SECONDARY_URL).contains('precondition_test')

   		assertTrue checkFile.exists()
   		String checkFileContent = checkFile.text
   		assertTrue checkFileContent.contains('database null: false')
   		assertTrue checkFileContent.contains('changeLog null: false')
   		assertTrue checkFileContent.contains('changeSet null: false')
   		assertTrue checkFileContent.contains('ctx null: false')
   		assertTrue checkFileContent.contains('resourceAccessor null: false')
   	}

    void testUpdateWithGroovyPreconditionFailForSecondaryDataSource() {

   		assertTableCount 1, SECONDARY_URL

   		copyTestChangelog 'test.customprecondition.changelog', SECONDARY_TEST_CHANGELOG

   		def dataFile = new File('target/check_data')
   		dataFile.deleteOnExit()
   		dataFile.delete()
   		assertFalse dataFile.exists()
   		dataFile.withWriter { it.write '15' }

   		executeAndCheck(['dbm-update', '--dataSource=secondary'], false)

   		assertTrue output.contains('Migration failed for change set changelog.cli.secondary-test.groovy::create_grails_table::burt:')
   		assertTrue output.contains('changelog.cli.secondary-test.groovy : value cannot be larger than 10')
   		assertTrue output.contains('Caused By: Preconditions Failed')

   		// original + 2 Liquibase but no new table from changelog
   		assertTableCount 3, SECONDARY_URL
   	}

    void testUpdateWithGroovyPreconditionFailAssertForSecondaryDataSource() {

   		assertTableCount 1, SECONDARY_URL

   		copyTestChangelog 'test.customprecondition.changelog', SECONDARY_TEST_CHANGELOG

   		def dataFile = new File('target/check_data')
   		dataFile.deleteOnExit()
   		dataFile.delete()
   		assertFalse dataFile.exists()
   		dataFile.withWriter { it.write '42' }

   		executeAndCheck(['dbm-update', '--dataSource=secondary'], false)

   		assertTrue output.contains('Migration failed for change set changelog.cli.secondary-test.groovy::create_grails_table::burt:')
   		assertTrue output.contains('changelog.cli.secondary-test.groovy : assert testValue != 42')
   		assertTrue output.contains('Caused By: Preconditions Failed')

   		// original + 2 Liquibase but no new table from changelog
   		assertTableCount 3, SECONDARY_URL
   	}

    void testUpdateWithGroovyPreconditionFailAssertWithMessageForSecondaryDataSource() {

   		assertTableCount 1, SECONDARY_URL

   		copyTestChangelog 'test.customprecondition.changelog', SECONDARY_TEST_CHANGELOG

   		def dataFile = new File('target/check_data')
   		dataFile.deleteOnExit()
   		dataFile.delete()
   		assertFalse dataFile.exists()
   		dataFile.withWriter { it.write '237' }

   		executeAndCheck(['dbm-update', '--dataSource=secondary'], false)

   		assertTrue output.contains('Migration failed for change set changelog.cli.secondary-test.groovy::create_grails_table::burt:')
   		assertTrue output.contains('changelog.cli.secondary-test.groovy : value cannot be 237. Expression: (testValue != 237). Values: testValue = 237')
   		assertTrue output.contains('Caused By: Preconditions Failed')

   		// original + 2 Liquibase but no new table from changelog
   		assertTableCount 3, SECONDARY_URL
   	}


    void testUpdateWithGroovyPreconditionUnhandledExceptionForSecondaryDataSource() {

   		assertTableCount 1, SECONDARY_URL

   		copyTestChangelog 'test.customprecondition.changelog', SECONDARY_TEST_CHANGELOG

   		def dataFile = new File('target/check_data')
   		dataFile.deleteOnExit()
   		dataFile.delete()
   		assertFalse dataFile.exists()
   		dataFile.withWriter { it.write '-1' }

   		executeAndCheck(['dbm-update', '--dataSource=secondary'], false)

   		assertTrue output.contains('Migration failed for change set changelog.cli.secondary-test.groovy::create_grails_table::burt:')
   		assertTrue output.contains('changelog.cli.secondary-test.groovy : unhandled runtime exception')
   		assertTrue output.contains('Caused By: Precondition Error')

   		// original + 2 Liquibase but no new table from changelog
   		assertTableCount 3, SECONDARY_URL
   	}

}
