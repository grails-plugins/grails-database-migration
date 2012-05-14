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

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class DbmGormDiffTests extends AbstractScriptTests {

	void testGormDiff_XML() {

		assertTableCount 1

		copyTestChangelog()
		executeAndCheck 'dbm-update'
		// original + 2 Liquibase + new person table
		assertTableCount 4

		def file = new File(CHANGELOG_DIR, '/gormdiff.xml')
		assertFalse file.exists()

		executeAndCheck(['dbm-gorm-diff', 'gormdiff.xml'])

		assertTrue file.exists()
		file.deleteOnExit()

		assertTrue output.contains('Starting dbm-gorm-diff')

		assertTableCount 4

		String diffOutput = file.text
		assertTrue diffOutput.contains('<databaseChangeLog ')
		assertTrue diffOutput.contains('<changeSet ')
		assertTrue diffOutput.contains('<createTable tableName="author">')
		assertTrue diffOutput.contains('<createTable tableName="book">')
		assertTrue diffOutput.contains('<addForeignKeyConstraint baseColumnNames="author_id" baseTableName="book" ')
		assertTrue diffOutput.contains('referencedColumnNames="id" referencedTableName="author" ')

		// no corresponding domain class
		assertTrue diffOutput.contains('<dropTable tableName="PERSON"/>')
	}

	void testGormDiff_Groovy() {

		assertTableCount 1

		copyTestChangelog()
		executeAndCheck 'dbm-update'
		// original + 2 Liquibase + new person table
		assertTableCount 4

		def file = new File(CHANGELOG_DIR, '/gormdiff.groovy')
		assertFalse file.exists()

		executeAndCheck(['dbm-gorm-diff', 'gormdiff.groovy'])

		assertTrue file.exists()
		file.deleteOnExit()

		assertTrue output.contains('Starting dbm-gorm-diff')

		assertTableCount 4

		String diffOutput = file.text

		assertTrue diffOutput.contains('databaseChangeLog = {')
		assertTrue diffOutput.contains('changeSet(author: ')
		assertTrue diffOutput.contains('createTable(tableName: "author") {')
		assertTrue diffOutput.contains('createTable(tableName: "book") {')
		assertTrue diffOutput.contains('addForeignKeyConstraint(baseColumnNames: "author_id", baseTableName: "book", ')
		assertTrue diffOutput.contains('referencedColumnNames: "id", referencedTableName: "author"')

		// no corresponding domain class
		assertTrue diffOutput.contains('dropTable(tableName: "PERSON")')
	}

	void testGormDiff_STDOUT() {

		assertTableCount 1

		copyTestChangelog()
		executeAndCheck 'dbm-update'
		// original + 2 Liquibase + new person table
		assertTableCount 4

		executeAndCheck(['dbm-gorm-diff'])
		assertTrue output.contains('Starting dbm-gorm-diff')

		assertTableCount 4

		assertTrue output.contains('<databaseChangeLog ')
		assertTrue output.contains('<changeSet ')
		assertTrue output.contains('<createTable tableName="author">')
		assertTrue output.contains('<createTable tableName="book">')
		assertTrue output.contains('<addForeignKeyConstraint baseColumnNames="author_id" baseTableName="book" ')
		assertTrue output.contains('referencedColumnNames="id" referencedTableName="author" ')

		// no corresponding domain class
		assertTrue output.contains('<dropTable tableName="PERSON"/>')
	}

    void testGormDiffForSecondaryDataSource_XML() {

   		assertTableCount 1, SECONDARY_URL

        copyTestChangelog('test.changelog', AbstractScriptTests.SECONDARY_TEST_CHANGELOG)
   		executeAndCheck (['dbm-update', '--dataSource=secondary'])
   		// original + 2 Liquibase + new person table
   		assertTableCount 4, SECONDARY_URL

   		def file = new File(CHANGELOG_DIR, '/gormdiff.xml')
   		assertFalse file.exists()

   		executeAndCheck(['dbm-gorm-diff', 'gormdiff.xml', '--dataSource=secondary'])

   		assertTrue file.exists()
   		file.deleteOnExit()

   		assertTrue output.contains('Starting dbm-gorm-diff')

   		assertTableCount 4, SECONDARY_URL

   		String diffOutput = file.text
        println diffOutput
   		assertTrue diffOutput.contains('<databaseChangeLog ')
   		assertTrue diffOutput.contains('<changeSet ')
   		assertTrue diffOutput.contains('<createTable tableName="author">')
   		assertTrue diffOutput.contains('<createTable tableName="book">')
   		assertTrue diffOutput.contains('<addForeignKeyConstraint baseColumnNames="author_id" baseTableName="book" ')
   		assertTrue diffOutput.contains('referencedColumnNames="id" referencedTableName="author" ')

   		// no corresponding domain class
   		assertTrue diffOutput.contains('<dropTable tableName="PERSON"/>')
   	}


    void testGormDiffForSecondaryDataSource_Groovy() {

   		assertTableCount 1, SECONDARY_URL

   		copyTestChangelog('test.changelog', SECONDARY_TEST_CHANGELOG)
   		executeAndCheck (['dbm-update', '--dataSource=secondary'])
   		// original + 2 Liquibase + new person table
   		assertTableCount 4, SECONDARY_URL

   		def file = new File(CHANGELOG_DIR, '/gormdiff.groovy')
   		assertFalse file.exists()

   		executeAndCheck(['dbm-gorm-diff', 'gormdiff.groovy', '--dataSource=secondary'])

   		assertTrue file.exists()
   		file.deleteOnExit()

   		assertTrue output.contains('Starting dbm-gorm-diff')

   		assertTableCount 4, SECONDARY_URL

   		String diffOutput = file.text

   		assertTrue diffOutput.contains('databaseChangeLog = {')
   		assertTrue diffOutput.contains('changeSet(author: ')
   		assertTrue diffOutput.contains('createTable(tableName: "author") {')
   		assertTrue diffOutput.contains('createTable(tableName: "book") {')
   		assertTrue diffOutput.contains('addForeignKeyConstraint(baseColumnNames: "author_id", baseTableName: "book", ')
   		assertTrue diffOutput.contains('referencedColumnNames: "id", referencedTableName: "author"')

   		// no corresponding domain class
   		assertTrue diffOutput.contains('dropTable(tableName: "PERSON")')
   	}

    void testGormDiffForSecondaryDataSource_STDOUT() {

   		assertTableCount 1, SECONDARY_URL

   		copyTestChangelog('test.changelog', SECONDARY_TEST_CHANGELOG)
   		executeAndCheck (['dbm-update', '-dataSource=secondary'])
   		// original + 2 Liquibase + new person table
   		assertTableCount 4, SECONDARY_URL

   		executeAndCheck(['dbm-gorm-diff', '--dataSource=secondary'])
   		assertTrue output.contains('Starting dbm-gorm-diff')

   		assertTableCount 4, SECONDARY_URL

   		assertTrue output.contains('<databaseChangeLog ')
   		assertTrue output.contains('<changeSet ')
   		assertTrue output.contains('<createTable tableName="author">')
   		assertTrue output.contains('<createTable tableName="book">')
   		assertTrue output.contains('<addForeignKeyConstraint baseColumnNames="author_id" baseTableName="book" ')
   		assertTrue output.contains('referencedColumnNames="id" referencedTableName="author" ')

   		// no corresponding domain class
   		assertTrue output.contains('<dropTable tableName="PERSON"/>')
   	}
}
