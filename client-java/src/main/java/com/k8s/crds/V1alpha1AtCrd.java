package com.k8s.crds;

import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionNames;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionSpec;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionStatus;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionVersion;
import io.kubernetes.client.openapi.models.V1CustomResourceValidation;
import io.kubernetes.client.openapi.models.V1JSONSchemaProps;
import io.kubernetes.client.openapi.models.V1ObjectMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class V1alpha1AtCrd {
    public V1CustomResourceDefinition getAtCrdBody() {
        return new V1CustomResourceDefinition().apiVersion("apiextensions.k8s.io/v1")
                                               .kind("CustomResourceDefinition")
                                               .metadata(getAtCrdMetadata())
                                               .spec(getAtCrdSpec())
                                               .status(getAtCrdStatus());
    }

    private V1ObjectMeta getAtCrdMetadata() {
        Map<String, String> annotations = new HashMap<>();
        annotations.put("controller-gen.kubebuilder.io/version", "");

        return new V1ObjectMeta().annotations(annotations)
                                 .creationTimestamp(null)
                                 .name("ats.cnat.programming-kubernetes.info");
    }

    private V1CustomResourceDefinitionSpec getAtCrdSpec() {
        return new V1CustomResourceDefinitionSpec().group("cnat.programming-kubernetes.info")
                                                   .names(getAtCrdSpecNames())
                                                   .scope("Namespaced")
                                                   .versions(getAtCrdSpecVersion());
    }

    private V1CustomResourceDefinitionStatus getAtCrdStatus() {
        return new V1CustomResourceDefinitionStatus().acceptedNames(getAtCrdStatusAcceptedNames())
                                                     .conditions(new ArrayList<>())
                                                     .storedVersions(new ArrayList<>());
    }

    private V1CustomResourceDefinitionNames getAtCrdSpecNames() {
        return new V1CustomResourceDefinitionNames().kind("At")
                                                    .listKind("AtList")
                                                    .plural("ats")
                                                    .singular("at");
    }

    private V1CustomResourceDefinitionNames getAtCrdStatusAcceptedNames() {
        return new V1CustomResourceDefinitionNames().kind("")
                                                    .plural("");
    }

    private List<V1CustomResourceDefinitionVersion> getAtCrdSpecVersion() {
        return Collections.singletonList(new V1CustomResourceDefinitionVersion().name("v1alpha1")
                                                                                .schema(getAtCrdSpecVersionSchema())
                                                                                .served(true)
                                                                                .storage(true));
    }

    private V1CustomResourceValidation getAtCrdSpecVersionSchema() {
        Map<String, V1JSONSchemaProps> properties = new HashMap<>();
        // apiVersion
        properties.put("apiVersion", new V1JSONSchemaProps().type("string")
            .description("'APIVersion defines the versioned schema of this representation " +
                "of an object. Servers should convert recognized schemas to the latest " +
                "internal value, and may reject unrecognized values. More info: " +
                "https://git.k8s.io/community/contributors/devel/sig-architecture/api-conventions.md#resources'"));
        // kind
        properties.put("kind", new V1JSONSchemaProps().type("string")
            .description("'Kind is a string value representing the REST resource this " +
                "object represents. Servers may infer this from the endpoint the client " +
                "submits requests to. Cannot be updated. In CamelCase. More info: " +
                "https://git.k8s.io/community/contributors/devel/sig-architecture/api-conventions.md#types-kinds'"));
        // metadata
        properties.put("metadata", new V1JSONSchemaProps().type("object"));

        // Sub-properties in spec
        Map<String, V1JSONSchemaProps> specSubProperties = new HashMap<>();
        // command
        specSubProperties.put("command", new V1JSONSchemaProps().type("string")
            .description("Command is the desired command (executed in a Bash shell) " +
                "to be executed."));
        // schedule
        specSubProperties.put("schedule", new V1JSONSchemaProps().type("string")
            .description("'Schedule is the desired time the command is supposed " +
                "to be executed. Note: the format used here is UTC time https://www.utctime.net'"));

        // spec
        properties.put("spec", new V1JSONSchemaProps().type("object")
            .description("AtSpec defines the desired state of At")
            .properties(specSubProperties));

        // Sub-properties in status
        Map<String, V1JSONSchemaProps> statusSubProperties = new HashMap<>();
        // phase
        statusSubProperties.put("phase", new V1JSONSchemaProps().type("string")
            .description("'Phase represents the state of the schedule: until the " +
                "command is executed it is PENDING, afterwards it is DONE.'"));

        // status
        properties.put("status", new V1JSONSchemaProps().type("object")
            .description("AtStatus defines the observed state of At")
            .properties(statusSubProperties));

        V1JSONSchemaProps openAPIV3Schema = new V1JSONSchemaProps().type("object")
            .description("At runs a command at a given schedule.")
            .properties(properties);

        return new V1CustomResourceValidation().openAPIV3Schema(openAPIV3Schema);
    }
}