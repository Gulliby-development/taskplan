package io.github.gulliby.taskplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PomTest {
    @Test
    void pinsJunitInOneProperty() throws IOException {
        String pom = pom();

        assertContains(pom, """
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    <junit.version>5.11.4</junit.version>
                  </properties>
                """);
        assertEquals(pom.indexOf("5.11.4"), pom.lastIndexOf("5.11.4"));
    }

    @Test
    void importsTheJunitBomInDependencyManagement() throws IOException {
        assertContains(pom(), """
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.junit</groupId>
                        <artifactId>junit-bom</artifactId>
                        <version>${junit.version}</version>
                        <type>pom</type>
                        <scope>import</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                """);
    }

    @Test
    void junitJupiterHasNoVersionOfItsOwn() throws IOException {
        assertContains(pom(), """
                  <dependencies>
                    <dependency>
                      <groupId>org.junit.jupiter</groupId>
                      <artifactId>junit-jupiter</artifactId>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                """);
    }

    @Test
    void keepsTheRestOfThePomAsItWas() throws IOException {
        String pom = pom();

        assertContains(pom, "  <groupId>io.github.gulliby</groupId>\n");
        assertContains(pom, "  <artifactId>taskplan</artifactId>\n");
        assertContains(pom, "  <version>0.1.0</version>\n");
        assertContains(pom, "  <packaging>jar</packaging>\n");
        assertContains(pom, "    <maven.compiler.release>21</maven.compiler.release>\n");
        assertContains(pom, """
                        <artifactId>maven-surefire-plugin</artifactId>
                        <version>3.5.2</version>
                """);
    }

    @Test
    void runsOnTheJunitVersionOfThePin() {
        assertEquals("5.11.4", Test.class.getPackage().getImplementationVersion());
    }

    @Test
    void readmeNamesTheSinglePin() throws IOException {
        String readme = Files.readString(Path.of("README.md"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");

        assertContains(readme, "JUnit is pinned in one place, the `junit.version` property of `pom.xml`;"
                + " the JUnit BOM in `dependencyManagement` keeps every JUnit artifact on that version.\n");
    }

    private static String pom() throws IOException {
        return Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static void assertContains(String text, String expected) {
        assertTrue(text.contains(expected), () -> "missing required content:\n" + expected);
    }
}
