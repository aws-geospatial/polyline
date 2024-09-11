// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package software.amazon.location.polyline

/** The version of flexible-polyline that's supported by this implementation */
internal const val FlexiblePolylineFormatVersion = 1

internal class EncodeException(override val message : String, val err : Polyline.EncodeError) : Exception(message)
internal class DecodeException(override val message : String, val err : Polyline.DecodeError) : Exception(message)
