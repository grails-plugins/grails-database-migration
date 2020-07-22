/*
 * Copyright 2015 original authors
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
package org.grails.plugins.databasemigration.command

import grails.dev.commands.ApplicationCommand
import grails.persistence.Entity
import liquibase.exception.UnexpectedLiquibaseException
import org.grails.plugins.databasemigration.DatabaseMigrationException

import java.rmi.UnexpectedException

class DbmGormDiffCommandSpec extends ApplicationContextDatabaseMigrationCommandSpec {

    final Class<ApplicationCommand> commandClass = DbmGormDiffCommand

    def setup() {
        sql.executeUpdate 'CREATE TABLE PUBLIC.author (id BIGINT AUTO_INCREMENT NOT NULL, version BIGINT NOT NULL, name VARCHAR(255) NOT NULL, CONSTRAINT authorPK PRIMARY KEY (id));'
    }

    def "suppress field validation"() {
        when: 'an invalid option is given'
        config.grails.plugin.databasemigration.suppressFields=['Invalid']
        command.handle(getExecutionContext())
        then:
        UnexpectedLiquibaseException parseError = thrown(UnexpectedLiquibaseException)
        parseError.message == 'Unable to parse suppressed field: Invalid'

        when: 'an invalid type is given'
        config.grails.plugin.databasemigration.suppressFields=['Invalid:field']
        command.handle(getExecutionContext())
        then:
        UnexpectedLiquibaseException typeError = thrown(UnexpectedLiquibaseException)
        typeError.cause instanceof ClassNotFoundException
        typeError.message == 'java.lang.ClassNotFoundException: liquibase.structure.core.Invalid'

        when: 'an invalid field is given'
        config.grails.plugin.databasemigration.suppressFields=['Index:ignored']
        command.handle(getExecutionContext())
        then:
        noExceptionThrown()
    }

    def "diffs GORM classes suppress nullable filed"() {
        given: 'generate the book table with a nullabble author'
            sql.executeUpdate'CREATE TABLE PUBLIC.book (id BIGINT AUTO_INCREMENT NOT NULL, version BIGINT NOT NULL, author_id BIGINT NULL, title VARCHAR(255) NOT NULL, CONSTRAINT bookPK PRIMARY KEY (id));'

        when: 'generate the difference'
            command.handle(getExecutionContext())

        then: 'the not nullable constraint added'
            def output = extractOutput(outputCapture).replaceAll(/\s/,"")
            output ==~ '''
databaseChangeLog = \\{

    changeSet\\(author: ".+?", id: ".+?"\\) \\{
        addForeignKeyConstraint\\(baseColumnNames: "author_id", baseTableName: "book", constraintName: "FK.+?", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "author", validate: "true"\\)
    \\}
    
    changeSet\\(author: ".+?", id: ".+?"\\) \\{
        addNotNullConstraint\\(columnDataType: "bigint", columnName: "author_id", tableName: "book", validate: "true"\\)
    \\}
\\}
'''.replaceAll(/\s/,"")

        when: 'nullable constraint is suppressed'
            config.grails.plugin.databasemigration.suppressFields=['Column:nullable']
            command.handle(getExecutionContext())

        then: 'only the foreign key difference is generated'
        def suppressed = extractOutput(outputCapture).replaceAll(/\s/,"")
        suppressed ==~ '''
databaseChangeLog = \\{

    changeSet\\(author: ".+?", id: ".+?"\\) \\{
        addForeignKeyConstraint\\(baseColumnNames: "author_id", baseTableName: "book", constraintName: "FK.+?", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "author", validate: "true"\\)
    \\}    
\\}
'''.replaceAll(/\s/,"")

    }

    def "diffs GORM classes against a database and generates a changelog to STDOUT"() {
        when:
        command.handle(getExecutionContext())

        then:
            def output = extractOutput(outputCapture).replaceAll(/\s/,"")
            output ==~ '''
databaseChangeLog = \\{

    changeSet\\(author: ".+?", id: ".+?"\\) \\{
        createTable\\(tableName: "book"\\) \\{
            column\\(autoIncrement: "true", name: "id", type: "BIGINT"\\) \\{
                constraints\\(nullable: "false", primaryKey: "true", primaryKeyName: "bookPK"\\)
            \\}

            column\\(name: "version", type: "BIGINT"\\) \\{
                constraints\\(nullable: "false"\\)
            \\}

            column\\(name: "author_id", type: "BIGINT"\\) \\{
                constraints\\(nullable: "false"\\)
            \\}

            column\\(name: "title", type: "VARCHAR\\(255\\)"\\) \\{
                constraints\\(nullable: "false"\\)
            \\}
        \\}
    \\}

    changeSet\\(author: ".+?", id: ".+?"\\) \\{
        addForeignKeyConstraint\\(baseColumnNames: "author_id", baseTableName: "book", constraintName: "FK.+?", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "author", validate: "true"\\)
    \\}
\\}
'''.replaceAll(/\s/,"")
    }

    def "diffs GORM classes against a database and generates a changelog to a file given as arguments"() {
        given:
            def filename = 'changelog.groovy'

        when:
            command.handle(getExecutionContext(filename))

        then:
            def output = new File(changeLogLocation, filename).text?.replaceAll(/\s/,"")
            output =~ '''
databaseChangeLog = \\{

    changeSet\\(author: ".+?", id: ".+?"\\) \\{
        createTable\\(tableName: "book"\\) \\{
            column\\(autoIncrement: "true", name: "id", type: "BIGINT"\\) \\{
                constraints\\(nullable:"false", primaryKey: "true", primaryKeyName: "bookPK"\\)
            \\}

            column\\(name: "version", type: "BIGINT"\\) \\{
                constraints\\(nullable: "false"\\)
            \\}

            column\\(name: "author_id", type: "BIGINT"\\) \\{
                constraints\\(nullable: "false"\\)
            \\}

            column\\(name: "title", type: "VARCHAR\\(255\\)"\\) \\{
                constraints\\(nullable: "false"\\)
            \\}
        \\}
    \\}

    changeSet\\(author: ".+?", id: ".+?"\\) \\{
        addForeignKeyConstraint\\(baseColumnNames: "author_id", baseTableName: "book", constraintName: "FK.+?", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "author", validate: "true"\\)
    \\}
\\}
'''.replaceAll(/\s/,"")
    }

    def "an error occurs if changeLogFile already exists"() {
        given:
            def filename = 'changelog.yml'
            def changeLogFile = new File(changeLogLocation, filename)
            assert changeLogFile.createNewFile()

        when:
            command.handle(getExecutionContext(filename))

        then:
            def e = thrown(DatabaseMigrationException)
            e.message == "ChangeLogFile ${changeLogFile.canonicalPath} already exists!"
    }

    @Override
    protected Class[] getDomainClasses() {
        [Book, Author] as Class[]
    }
}
