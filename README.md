# Charity Market

## Project overview

**Charity Market** is a multi-device management system for charity shops, fundraising markets, and similar volunteer-run sales activities.

The project is built around a simple idea: donated goods should be easy to trace from the person who donated them, through inventory, to the transaction in which they were sold. At the same time, the system should remain practical enough to run on a single computer for a small event and structured enough to support several users and devices when the operation grows.

Charity Market therefore combines donor management, inventory tracking, sales recording, user permissions, and shared application settings behind one central server.

## The idea behind the project

Many small charity markets are managed with spreadsheets, handwritten labels, separate lists of donors, and manual calculations after an event. That can work at a very small scale, but it becomes difficult to answer basic questions consistently:

- Who donated this item?
- Is the item still available?
- What condition was it in when it was received?
- What price was suggested?
- What price was it actually sold for?
- Which sale contained it?
- Was that sale later voided?
- How much has been raised through completed sales?
- Which users are allowed to change inventory, create sales, or administer the system?

Charity Market is intended to keep those relationships in one system instead of spreading them across disconnected documents.

The core flow is:

```text
Donor
  |
  v
Donated items
  |
  v
Inventory
  |
  v
Sale / transaction
  |
  v
Recorded proceeds and history
```

The server is the source of truth. Desktop and Android applications are clients of the same data and business rules, so multiple people can work with the same market without each maintaining a separate copy of the information.

## Core concepts

### Donors

A donor represents the person or organization that contributed goods to the market.

The system stores information such as:

- name;
- email address;
- phone number;
- comments;
- creation and update timestamps.

Items are linked back to their donor, preserving the origin of the stock.

This relationship makes it possible for the application to treat a charity market as more than a generic point-of-sale system: the provenance of each donated item remains part of the data model.

### Items and inventory

Every donated item becomes an inventory record.

An item can contain:

- a unique item code;
- a name;
- its donor;
- its condition;
- a suggested price;
- comments;
- an inventory status;
- creation and update timestamps.

The current status model includes:

- `AVAILABLE`
- `SOLD`
- `DAMAGED`
- `MISSING`

The condition model ranges from new to poor condition:

- `NEW`
- `LIKE_NEW`
- `GOOD`
- `ACCEPTABLE`
- `POOR`

The purpose of the inventory layer is to give volunteers and staff a shared view of what the market currently has and what has already left the available stock.

### Sales

A sale represents a completed market transaction.

A sale contains one or more sale lines, with each line linking an inventory item to the final price paid for it. The sale itself records information including:

- sale date and time;
- payment method;
- total value;
- comments;
- status;
- void information when applicable.

Supported payment categories currently include:

- cash;
- card;
- other.

Sales can be either:

- `COMPLETED`
- `VOIDED`

Voiding a transaction is modeled separately from simply deleting it. This reflects an important idea in the project: operational mistakes should be correctable without pretending that the original transaction never existed.

## Users and responsibilities

Charity Market is designed for environments where different people have different responsibilities.

The server defines roles including:

- `SYSTEM_ADMINISTRATOR`
- `MARKET_MANAGER`
- `INVENTORY_MANAGER`
- `SELLER`
- `AUDITOR`

The client applications use role rules to determine which parts of the interface are appropriate for the signed-in user, while the server remains responsible for enforcing authorization.

Examples of the intended separation include:

- system administrators managing users and system-level operations;
- managers overseeing normal market activity;
- inventory managers maintaining donors and stock;
- sellers accessing the information needed to record transactions.

This makes the system suitable for a charity event where volunteers should not all have unrestricted administrative access.

## Authentication and session model

Users sign in to a Charity Market server and receive a JWT access token.

The application includes a mandatory password-change flow for accounts that require it, and it handles users whose accounts become suspended or disabled.

Clients periodically verify the authenticated session so that account changes made on the server are reflected while an application is running.

The authentication model reinforces a central design principle of Charity Market: permissions and account state belong to the server, not to an individual desktop or phone.

## Desktop application

The desktop application is the most flexible Charity Market client.

It can operate in two different ways.

### Remote client mode

The desktop application can connect to an existing Charity Market server over the network.

In this model:

```text
Desktop client
      |
      v
Charity Market server
      |
      v
Shared database
```

This is suitable when several computers or Android devices need to use the same installation.

### Local host mode

The desktop application can also start its own local Quarkus server backed by SQLite.

In this model:

```text
Desktop application
      |
      +--> Local Charity Market server
                 |
                 +--> SQLite database
```

The desktop application still behaves as a normal client of that server.

This allows a small charity market to run entirely from one Windows computer without requiring a separate server machine, while preserving the same client/server architecture used by larger deployments.

Local hosting is treated as a persistent operating mode. Logging out changes the authenticated user but does not stop the local server. The desktop application also contains lifecycle handling for monitoring the local server and controlling its shutdown.

## Android application

The Android application is a network client for Charity Market.

It allows the user to select a server, verify that the server is healthy, authenticate, and work with the same shared API used by the desktop application.

The Android client shares API models, authentication concepts, role rules, settings behavior, and other common logic with the rest of the Kotlin Multiplatform project.

The intention is that a phone or tablet can become another workstation in the same charity market rather than creating a separate mobile-only data store.

## Shared client layer

The project contains a Kotlin Multiplatform `shared` module used by both desktop and Android code.

This layer includes common concepts such as:

- API communication;
- request and response models;
- JWT session handling;
- application destinations;
- role-based client rules;
- server settings;
- data refresh behavior.

The shared layer keeps both clients aligned with the same server contract and reduces the chance that desktop and Android implement the same business interaction differently.

## Server

The backend is a Java 17 Quarkus application.

It acts as the central authority for:

- authentication;
- authorization;
- users;
- donors;
- items;
- sales;
- application settings;
- synchronization state;
- database persistence.

The client applications access those capabilities through a REST API.

This separation is important to the project because the business rules are not intended to live only in a graphical client. A user should not be able to bypass a permission simply by using a different Charity Market client.

## Shared settings and synchronization

Charity Market also treats some settings as properties of the installation rather than of one device.

The server stores global settings including:

- currency code;
- whether automatic refresh is enabled;
- whether users may customize refresh behavior;
- default refresh interval;
- minimum and maximum refresh intervals.

Clients can monitor a server-side data version and refresh their views when shared data changes.

The goal is to make several connected devices feel like parts of the same system, rather than independent applications that happen to use the same database.

## Deployment philosophy

Charity Market is deliberately designed to fit more than one scale of deployment.

### Small or portable installation

For a single computer or a small event:

```text
Desktop application
+ local Quarkus server
+ SQLite
```

This minimizes infrastructure and keeps the complete installation self-contained.

### Standalone shared server

A dedicated server can run Charity Market with SQLite for a simple centralized installation.

```text
Desktop / Android clients
          |
          v
Charity Market server
          |
          v
SQLite
```

### Multi-user database server

For installations where a dedicated database is preferred, the server includes support for PostgreSQL and MySQL profiles.

```text
Desktop / Android clients
          |
          v
Charity Market server
          |
          v
PostgreSQL or MySQL
```

The repository also contains Docker-oriented deployment configuration, allowing the server and its persistent data layer to be separated from the client applications.

## Project architecture

At a high level, the repository is divided into four main parts:

```text
charity-market
|
+-- desktopApp
|   Desktop client and local-server hosting
|
+-- androidApp
|   Android client
|
+-- shared
|   Common Kotlin Multiplatform client logic
|
+-- server
    Quarkus backend and persistence
```

This architecture supports one of the main goals of the project: the same market data and rules should be usable from different devices without duplicating the backend logic.

## Design principles

### Keep the donated item traceable

An item is connected to a donor and remains identifiable by its own code. The application is structured around maintaining that relationship throughout the item's lifecycle.

### Keep the server authoritative

Authentication, permissions, business operations, and persistent data belong to the server. Clients present and interact with those capabilities rather than becoming independent sources of truth.

### Preserve meaningful history

Sales support explicit completed and voided states. The project models corrections as part of the history instead of treating all mistakes as records that should simply disappear.

### Support both small and larger installations

A one-computer charity market should not need enterprise infrastructure. At the same time, moving to a central server should not require replacing the application model.

### Separate responsibilities

Different users should see and perform the operations relevant to their role. Inventory work, selling, management, and system administration are distinct responsibilities.

### Share behavior across clients

Desktop and Android use common client logic wherever practical so that the same server behaves consistently regardless of the device being used.

## What Charity Market is

Charity Market is not intended to be just a generic inventory database or a generic cash register.

Its central concept is the lifecycle of donated goods:

```text
A donor gives an item
        |
        v
The item enters inventory
        |
        v
The item is made available for sale
        |
        v
A seller records the transaction
        |
        v
The sale becomes part of the market history
```

Around that lifecycle, the project adds the operational pieces needed for real use: users, permissions, authentication, shared settings, multiple clients, persistent storage, and flexible deployment.

The result is intended to be a practical digital foundation for running a charity market while keeping the system simple enough for volunteers and small organizations to operate.
