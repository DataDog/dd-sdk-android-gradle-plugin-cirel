/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2020-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.internal

internal data class DdAppIdentifier(
    val serviceName: String,
    val version: String,
    val variant: String
) {

    override fun toString(): String {
        return "`service:$serviceName`, `version:$version`, `variant:$variant`"
    }
}
