package org.vertexium.cypher.tck;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(
        format = "pretty",
        features = "classpath:org/vertexium/cypher/tck/features/ListComprehension.feature",
        glue = "org.vertexium.cypher.glue"
)
public class ListComprehensionTest {
}