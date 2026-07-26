# Đặc Tả API - LingoLens

Toàn bộ các API đều sử dụng định dạng dữ liệu **JSON** ở phần Body. Cổng chạy mặc định của máy chủ Backend là `8080`.

---

## 1. Nhóm API Xác thực (Authentication APIs)

Các API này không yêu cầu token xác thực và có tiền tố `/api/v1/auth/`.

### 1.1. Đăng ký tài khoản (Register)
- **Endpoint:** `POST /api/v1/auth/register`
- **Request Body:**
```json
{
  "email": "user@gmail.com",
  "password": "strongpassword123",
  "name": "Nguyen Van A"
}
```
- **Response (200 OK):**
```json
{
  "message": "User registered successfully with ID: 2"
}
```
- **Response Lỗi (400 Bad Request):**
```json
{
  "email": "Email is invalid",
  "password": "Password must be at least 8 characters"
}
```

### 1.2. Đăng nhập (Login)
- **Endpoint:** `POST /api/v1/auth/login`
- **Request Body:**
```json
{
  "email": "user@gmail.com",
  "password": "strongpassword123"
}
```
- **Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "7c98075c-a55e-4cfc-a496-d8cb84523b12",
  "tokenType": "Bearer",
  "email": "user@gmail.com",
  "name": "Nguyen Van A",
  "roles": ["ROLE_USER"]
}
```
- **Response Lỗi (401 Unauthorized):**
```json
{
  "error": "Unauthorized",
  "message": "Bad credentials"
}
```

### 1.3. Đổi Access Token bằng Refresh Token (Refresh)
- **Endpoint:** `POST /api/v1/auth/refresh`
- **Request Body:**
```json
{
  "refreshToken": "7c98075c-a55e-4cfc-a496-d8cb84523b12"
}
```
- **Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "8d98075c-b55e-5cfc-b496-e8cb84523c34",
  "tokenType": "Bearer",
  "email": "user@gmail.com",
  "name": "Nguyen Van A",
  "roles": ["ROLE_USER"]
}
```

### 1.4. Đăng xuất (Logout)
- **Endpoint:** `POST /api/v1/auth/logout`
- **Headers:** `Authorization: Bearer <accessToken>`
- **Response (200 OK):**
```json
{
  "message": "Logged out successfully"
}
```

---

## 2. Nhóm API Người dùng (Secured APIs)

Các API này yêu cầu Header `Authorization: Bearer <accessToken>`.

### 2.1. Lấy thông tin tài khoản đang đăng nhập (Get Profile)
- **Endpoint:** `GET /api/v1/users/me`
- **Response (200 OK):**
```json
{
  "id": 2,
  "email": "user@gmail.com",
  "name": "Nguyen Van A",
  "targetLevel": "B1",
  "streakDays": 5,
  "roles": ["ROLE_USER"]
}
```
- **Response Lỗi (401 Unauthorized):**
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource"
}
```
