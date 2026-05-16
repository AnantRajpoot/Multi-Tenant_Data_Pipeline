<<<<<<< HEAD
# 🚀 Multi-Tenant Data Pipeline

A **Spring Boot–based ETL pipeline** for ingesting, transforming, and loading data with support for **CSV and JSON formats**.

---

## ✨ Key Features

### 📥 Data Ingestion
- 📄 **CSV** — configurable & streaming support  
- 🔗 **JSON** — object, array, JSONL formats  
- 🧠 **Schema Inference** — automatic data type detection  

---

### 🔄 Transformations
- 🔍 **Filter** — `==, !=, <, >, AND, OR`  
- 🛠️ **Map** — string & math operations  
- 📊 **Aggregate** — `GROUP BY`, `SUM`, `AVG`, `COUNT`, etc.  

---

### 📤 Data Loading
- 🗄️ **MySQL** — append, overwrite, upsert  
- 📁 **File Export** — CSV & JSON (optional compression)  

---

### ⚙️ Execution Tracking
- 🔄 **Status Flow** — `PENDING → RUNNING → SUCCESS / FAILED`  
- 📈 **Metrics** — records processed, execution duration  
- ⚠️ **Error Handling** — supports partial failures  

---

### 🌐 APIs
- 🔧 **CRUD** — manage pipeline definitions  
- ▶️ **Execution** — trigger & monitor pipeline runs  

---

## 💡 Overview

This project provides a **scalable foundation** for building **multi-tenant data processing systems**, enabling efficient ETL workflows with flexible configurations.

---
## Phase 1 (Overview)

This repository implements a Spring Boot (Java 17) based multi-tenant data pipeline. It supports ingestion from CSV/JSON sources, an extensible transformation library, and multiple loading options.

### Features (Phase 1)
- Pipeline definition and validation
- Ingestion (CSV/JSON file streaming, schema inference)
- Transformation (filter, map, aggregate)
- Loading (H2/MySQL support, file exports)
- Execution status and progress tracking
- REST API endpoints for pipeline management and job runs

### Tech Stack
- Java 17
- Spring Boot
- Maven
- H2 (test) / MySQL (production)

### Getting Started
1. Build: `mvn clean package`
2. Run: `mvn spring-boot:run`
3. API: See `/pipelines` endpoints

---
This project is scaffolded and developed in the `demo2` folder.
