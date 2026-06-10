/** OpenAPI spec + Swagger UI routes — `/api/items` only (mirrors Java/Rust scope). */

import type { Express } from "express";
import swaggerUi from "swagger-ui-express";

export const openApiSpec = {
  openapi: "3.0.3",
  info: {
    title: "Exercises React Node API",
    version: "1.0",
    description:
      "REST API under `/api/items` (Express proxies to Java). Health, probe, and observability routes are excluded.",
  },
  tags: [
    {
      name: "Items",
      description: "Shared PostgreSQL `items` table via Java backend (Flyway schema)",
    },
  ],
  paths: {
    "/api/items": {
      get: {
        tags: ["Items"],
        summary: "List items",
        description: "Proxies to Java `GET /api/items`. Forwards `X-Request-ID` when present.",
        responses: {
          "200": {
            description: "All items",
            content: {
              "application/json": {
                schema: {
                  type: "array",
                  items: { $ref: "#/components/schemas/Item" },
                },
              },
            },
          },
          "502": {
            description: "Java backend unreachable or error",
            content: {
              "application/json": {
                schema: { $ref: "#/components/schemas/ApiError" },
              },
            },
          },
        },
      },
      post: {
        tags: ["Items"],
        summary: "Create item",
        description: "Proxies to Java `POST /api/items` with JSON body. Forwards `X-Request-ID` when present.",
        requestBody: {
          required: true,
          content: {
            "application/json": {
              schema: { $ref: "#/components/schemas/CreateItemRequest" },
            },
          },
        },
        responses: {
          "201": {
            description: "Created",
            content: {
              "application/json": {
                schema: { $ref: "#/components/schemas/Item" },
              },
            },
          },
          "400": {
            description: "Blank name",
            content: {
              "application/json": {
                schema: { $ref: "#/components/schemas/ApiError" },
              },
            },
          },
          "502": {
            description: "Java backend unreachable or error",
            content: {
              "application/json": {
                schema: { $ref: "#/components/schemas/ApiError" },
              },
            },
          },
        },
      },
    },
  },
  components: {
    schemas: {
      Item: {
        type: "object",
        required: ["id", "name", "createdAt"],
        properties: {
          id: { type: "integer", format: "int64", example: 1 },
          name: { type: "string", example: "Widget" },
          createdAt: { type: "string", example: "2026-01-01T00:00:00Z" },
        },
      },
      CreateItemRequest: {
        type: "object",
        required: ["name"],
        properties: {
          name: { type: "string", example: "New item" },
        },
      },
      ApiError: {
        type: "object",
        required: ["error"],
        properties: {
          error: { type: "string", example: "upstream error" },
        },
      },
    },
  },
} as const;

export function registerOpenApiRoutes(app: Express): void {
  app.get("/api-docs/openapi.json", (_req, res) => {
    res.json(openApiSpec);
  });
  app.use("/swagger-ui", swaggerUi.serve, swaggerUi.setup(openApiSpec, {
    customSiteTitle: "Exercises React Node API",
  }));
}
