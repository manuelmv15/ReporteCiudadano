# 08 — Android: Modelos de Datos

POJOs usados por Retrofit/Gson para serializar requests y deserializar responses.

## Requests

### LoginRequest
```java
String email
String password
```

### RegisterRequest
```java
String name
String email
String password
String password_confirmation
```

### GoogleLoginRequest
```java
String id_token   // ID token de Google Sign-In
```

### ForgotPasswordRequest
```java
String email
```

### ResetPasswordRequest
```java
String email
String token       // código de 6 dígitos
String password
String password_confirmation
```

### ReportRequest
```java
int category_id
double latitude
double longitude
String description
// foto se manda por Multipart separado en PUT; POST usa JSON body
```

### VoteRequest
```java
String type        // "confirm" | "resolve"
double latitude    // ubicación del usuario al votar
double longitude
```

### UpdateProfileRequest
```java
String name
```

---

## Responses

### AuthResponse
```java
boolean success
String token       // Bearer token de Sanctum
UserData user      // inner class
String message

class UserData {
    int id
    String name
    String email
    String avatar_url
    int score
    String level      // "nuevo" | "colaborador" | "guardian" | "experto"
    boolean onboarding_done
}
```

### ReportResponse
```java
boolean success
PaginatedData reports   // inner class con data + pagination meta

class PaginatedData {
    List<ReportData> data
    // meta de paginación (current_page, last_page, per_page, total)
}

class ReportData {
    int id
    double latitude
    double longitude
    String status
    String description
    int user_id
    String photo_path
    Category category
    UserInfo user
    int votes_confirm
    int votes_resolve
    String user_vote   // "confirm" | "resolve" | null
    String created_at  // ISO-8601
    String updated_at

    class Category {
        int id
        String name
        String slug
        String icon
    }
    class UserInfo {
        int id
        String name
        String avatar_url
        int score
        String level
    }
}
```

Método auxiliar: `getData()` retorna la lista de `ReportData`.

### ReportDetailResponse
```java
boolean success
ReportResponse.ReportData report   // mismo ReportData pero con votes object
// votes: { confirm: int, resolve: int }
// user_vote: "confirm" | "resolve" | null
// user_voted_at: ISO-8601 | null
```

### CreateReportResponse
```java
boolean success
String message
ReportResponse.ReportData report
```

### ReportStreamResponse
```java
boolean success
String timestamp   // ISO-8601 del momento de la respuesta
int count
List<StreamReport> reports

class StreamReport {
    int id
    double latitude
    double longitude
    String status
    String description
    int category_id
    Category category
    int user_id
    UserInfo user
    int votes_confirm
    int votes_resolve
    String photo_path
    String created_at
    String updated_at
}
```

### HeatmapResponse
```java
boolean success
List<Point> points

class Point {
    double latitude
    double longitude
}
```

### VoteResponse
```java
boolean success
String message
VoteData data

class VoteData {
    String type
    int user_id
    int report_id
    int votes_confirm
    int votes_resolve
    String status    // estado del reporte después del voto
    String created_at
}
```

### MyVotesResponse
```java
boolean success
PaginatedVotes votes

class PaginatedVotes {
    List<VoteItem> data

    class VoteItem {
        int id
        int report_id
        int user_id
        String type     // "confirm" | "resolve"
        String created_at
        ReportSummary report  // reporte al que corresponde el voto

        class ReportSummary {
            int id
            int category_id
            String description
            String status
            String photo_path
            int votes_confirm
            int votes_resolve
            Category category
        }
    }
}
```

### AvatarUploadResponse
```java
boolean success
String message
String avatar_url   // URL pública del avatar subido
```

### SimpleResponse
```java
boolean success
String message
```

---

## Report.java (legacy/alternativo)

Clase simple con campos básicos del reporte. Puede ser un modelo alternativo de uso interno (no el principal de Retrofit).

---

## UpdateReportRequest
```java
String description
// La foto se manda como MultipartBody.Part en Retrofit
```
