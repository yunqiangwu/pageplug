package com.appsmith.external.models;

import lombok.Getter;

@Getter
public enum ErrorType {
    ARGUMENT_ERROR,
    CONFIGURATION_ERROR,
    DATASOURCE_CONFIGURATION_ERROR,
    CONNECTIVITY_ERROR,
    AUTHENTICATION_ERROR,
    BAD_REQUEST,
    INTERNAL_ERROR,
    ACTION_CONFIGURATION_ERROR,
    GIT_CONFIGURATION_ERROR,
    GIT_ACTION_EXECUTION_ERROR,
    GIT_UPSTREAM_CHANGES_PUSH_EXECUTION_ERROR,
    REPOSITORY_NOT_FOUND,
    EE_FEATURE_ERROR
}
