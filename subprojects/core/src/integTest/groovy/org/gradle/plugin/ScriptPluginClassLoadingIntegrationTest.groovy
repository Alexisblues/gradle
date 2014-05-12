/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugin

import org.gradle.groovy.scripts.ScriptSource
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

import static org.gradle.util.Matchers.containsText

class ScriptPluginClassLoadingIntegrationTest extends AbstractIntegrationSpec {

    @Issue("http://issues.gradle.org/browse/GRADLE-3069")
    def "second level and beyond script plugins have same class loader scope as original target"() {
        when:
        file("buildSrc/src/main/java/pkg/Thing.java") << """
            package pkg;
            public class Thing {
              public String getMessage() { return "hello"; }
            }
        """

        file("plugin1.gradle") << """
            task sayMessageFrom1 << { println new pkg.Thing().getMessage() }
            apply from: 'plugin2.gradle'
        """

        file("plugin2.gradle") << """
            task sayMessageFrom2 << { println new pkg.Thing().getMessage() }
            apply from: 'plugin3.gradle'
        """

        file("plugin3.gradle") << """
            task sayMessageFrom3 << { println new pkg.Thing().getMessage() }
        """

        buildScript "apply from: 'plugin1.gradle'"

        then:
        succeeds "sayMessageFrom1", "sayMessageFrom2", "sayMessageFrom3"
        output.contains "hello"
    }

    @Issue("http://issues.gradle.org/browse/GRADLE-3079")
    def "methods defined in script are available to used script plugins"() {
        given:
        buildScript """
          def addTask(project) {
            project.tasks.create("hello").doLast { println "hello from method" }
          }

          apply from: "script.gradle"
        """

        file("script.gradle") << "addTask(project)"

        when:
        succeeds "hello"

        then:
        output.contains "hello from method"
    }

    @Issue("http://issues.gradle.org/browse/GRADLE-3082")
    def "can use apply block syntax to apply multiple scripts"() {
        given:
        buildScript """
          apply {
            from "script1.gradle"
            from "script2.gradle"
          }
        """

        file("script1.gradle") << "task hello1 << { println 'hello from script1' }"
        file("script2.gradle") << "task hello2 << { println 'hello from script2' }"

        when:
        succeeds "hello1", "hello2"

        then:
        output.contains "hello from script1"
        output.contains "hello from script2"
    }

    def "separate classloaders are used when using multi apply syntax"() {
        given:
        buildScript """
          apply {
            from "script1.gradle"
            from "script2.gradle"
          }
        """

        file("script1.gradle") << "class Foo {}"
        file("script2.gradle") << "new Foo()"

        when:
        fails "tasks"

        then:
        failure.assertHasFileName("Script '${file("script2.gradle").absolutePath}'")
        failure.assertThatCause(containsText("unable to resolve class Foo"))
    }
}
