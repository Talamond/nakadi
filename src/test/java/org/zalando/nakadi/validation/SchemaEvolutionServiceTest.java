package org.zalando.nakadi.validation;

import com.google.common.collect.Lists;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.zalando.nakadi.domain.EventType;
import org.zalando.nakadi.utils.EventTypeTestBuilder;
import org.zalando.nakadi.validation.schema.NotSchemaConstraint;
import org.zalando.nakadi.validation.schema.SchemaConstraint;
import org.zalando.nakadi.validation.schema.SchemaEvolutionConstraint;
import org.zalando.nakadi.validation.schema.SchemaEvolutionIncompatibility;

import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.zalando.nakadi.utils.IsOptional.isAbsent;
import static org.zalando.nakadi.utils.TestUtils.readFile;

public class SchemaEvolutionServiceTest {
    private SchemaEvolutionService service;
    private SchemaEvolutionConstraint evolutionConstraint = mock(SchemaEvolutionConstraint.class);

    @Before
    public void setUp() {
        final List<SchemaConstraint> constraints = Lists.newArrayList(new NotSchemaConstraint());
        final List<SchemaEvolutionConstraint> evolutionConstraints= Lists.newArrayList(evolutionConstraint);

        this.service = new SchemaEvolutionService(constraints, evolutionConstraints);
    }

    @Test
    public void checkEvolutionConstraints() throws Exception {
        final EventTypeTestBuilder builder = EventTypeTestBuilder.builder();
        final EventType oldEventType = builder.build();
        final EventType newEventType = builder.build();

        Mockito.doReturn(Optional.empty()).when(evolutionConstraint).validate(oldEventType, newEventType);

        final Optional<SchemaEvolutionIncompatibility> incompatibility = service.evolve(oldEventType, newEventType);

        assertThat(incompatibility, isAbsent());

        verify(evolutionConstraint).validate(oldEventType, newEventType);
    }

    @Test
    public void checksJsonSchemaConstraints() throws Exception {
        final JSONArray invalidTestCases = new JSONArray(
                readFile("org/zalando/nakadi/validation/invalid-json-schema-examples.json"));

        for(final Object testCaseObject : invalidTestCases) {
            final JSONObject testCase = (JSONObject) testCaseObject;
            final Schema schema = SchemaLoader.load(testCase.getJSONObject("schema"));
            final List<String> errorMessages = testCase
                    .getJSONArray("errors")
                    .toList()
                    .stream()
                    .map(Object::toString)
                    .collect(toList());
            final String description = testCase.getString("description");


            assertThat(description, service.checkConstraints(schema).stream().map(Object::toString).collect(toList()),
                    is(errorMessages));
        }
    }
}