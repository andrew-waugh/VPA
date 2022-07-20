# VPA

Copyright Public Records Office Victoria 2022

License CC BY 4.0

## What is VERS?

This package is part of the Victorian Electronic Records Strategy (VERS)
software release. For more information about VERS see
[here](https://prov.vic.gov.au/recordkeeping-government/vers).

## What is the VPA?

The VERS Processing App (VPA) processes V2 and V3 VEOs to produce packages for submission
to the various PROV systems. It extracts the content (to the SAMS package), metadata (to
the AMS package), and the VEO itself (to the DAS package). Any errors are reported.

It may also be used as a standalone tool to test V2 and V3 VEOs.

## Using VPA

The VPA is run from the command line. 'vpa -help' will print a
precis of the command line options. The package contains a BAT file.

To use this package you also need to download the VERSCommon, V2Check, and neoVEO
packages, and thes must be placed in the same directory as the V2Generator package.

Structurally, the package is an Apache Netbeans project.
