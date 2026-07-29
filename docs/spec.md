# Техническое задание

## Описание

Цель данного модуля - реализация REST API, обеспечивающего доступ к файловому
хранилищу MinIO S3. Система должна позволять пользователям загружать и управлять
файлами, отслеживать историю загрузок и обеспечивать разграничение прав доступа
с использованием JWT-аутентификации.

## Сущности

**User**
- Integer id
- String username
- Status status (ACTIVE, BLOCKED)
- List events

**Event**
- Integer id
- User user
- File file
- Status status (CREATED, UPDATED, DELETED)
- LocalDateTime timestamp

**File**
- Integer id
- String name
- String location (MinIO S3 URL)
- Status status (ACTIVE, ARCHIVED)

## Функциональные требования

1. Поддержка CRUD-операций для сущностей User, Event и File.
2. При каждом запросе на загрузку файла автоматически создается соответствующее событие (Event).
3. Реализация архитектурного подхода на основе Spring WebFlux (реактивный стек).
4. Хранение файлов на MinIO с использованием AWS SDK.
5. Использование JWT-токенов для авторизации и разграничения прав доступа.
6. Инициализация и миграция БД с помощью Flyway.
7. Использование Spring Data JPA и Hibernate ORM.
8. Сборка проекта и управление зависимостями через Gradle.
9. Приложение должно быть докеризировано и готово к развертыванию в Docker-контейнере.
10. Тестирование API с использованием JUnit, Mockito и Testcontainers (интеграционные тесты).
11. Документация API посредством Swagger (OpenAPI).

## Уровни доступа

- **ADMIN** – полный доступ ко всем данным и операциям приложения.
- **MODERATOR** – права уровня USER, а также чтение всех User, чтение/изменение/удаление всех Events и Files.
- **USER** – только чтение своих данных и загрузка файлов для себя.

## Стек технологий

Java, Spring Boot, Data JPA, Security, MySQL, AWS SDK, JWT, Gradle, Flyway,
Docker, JUnit, Mockito, Swagger (OpenAPI).

## DDL скрипты

```sql
CREATE TABLE users (
  id INT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(255) NOT NULL,
  status VARCHAR(50) NOT NULL
);

CREATE TABLE files (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  location VARCHAR(500) NOT NULL,
  status VARCHAR(50) NOT NULL
);

CREATE TABLE events (
  id INT PRIMARY KEY AUTO_INCREMENT,
  user_id INT,
  file_id INT,
  status VARCHAR(50) NOT NULL,
  timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (file_id) REFERENCES files(id)
);
```

## Sequence Diagram

```
User -> API: JWT Authenticated Upload File Request
API -> SecurityFilter: validateToken()
SecurityFilter --> API: valid
API -> FileController: handleFileUpload()
FileController -> FileService: uploadToS3()
FileService -> AWS SDK: putObject()
AWS SDK --> FileService: success response with location
FileService -> FileRepository: persistFile()
FileService -> EventService: createEvent()
EventService -> EventRepository: persistEvent()
API <-- FileController: Success Response
```

## OpenAPI Specification (Swagger)

```yaml
openapi: 3.0.0
info:
  title: File Storage S3 API
  version: 1.0.0
paths:
  /files:
    post:
      summary: Upload a file to Yandex S3
      security:
        - bearerAuth: []
      requestBody:
        required: true
        content:
          multipart/form-data:
            schema:
              type: object
              properties:
                file:
                  type: string
                  format: binary
      responses:
        '201':
          description: File uploaded successfully
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/File'
  /files/{id}:
    get:
      summary: Retrieve a file by ID
      security:
        - bearerAuth: []
      parameters:
        - in: path
          name: id
          required: true
          schema:
            type: integer
      responses:
        '200':
          description: Successful retrieval of file
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/File'
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
  schemas:
    File:
      type: object
      properties:
        id:
          type: integer
        name:
          type: string
        location:
          type: string
        status:
          type: string
```