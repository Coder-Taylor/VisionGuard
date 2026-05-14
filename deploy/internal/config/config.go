package config

import (
	"log"
	"os"
)

type Config struct {
	ServerPort string

	DBHost string
	DBPort string
	DBUser string
	DBPass string
	DBName string

	RedisHost     string
	RedisPort     string
	RedisPassword string

	JWTSecret string

	DeviceUniqueCode      string
	DeviceXORKey          byte
	DeviceActivationToken string // 出厂共享激活口令，硬件首次激活时必须以 X-Activation-Token 头携带

	OCRServiceURL string

	DoubaoAPIKey string
	DoubaoAPIURL string

	PublicBaseURL string // 服务器公网 URL，用于生成豆包可访问的图片地址
}

func Load() *Config {
	jwtSecret := os.Getenv("JWT_SECRET")
	if jwtSecret == "" {
		log.Fatal("JWT_SECRET is required and must not be empty; refusing to start with an unsigned token surface")
	}
	if len(jwtSecret) < 32 {
		log.Fatal("JWT_SECRET must be at least 32 characters")
	}

	return &Config{
		ServerPort: getEnv("SERVER_PORT", "8888"),

		DBHost: getEnv("DB_HOST", "localhost"),
		DBPort: getEnv("DB_PORT", "5432"),
		DBUser: getEnv("DB_USER", "visionhub"),
		DBPass: getEnv("DB_PASSWORD", "visionhub"),
		DBName: getEnv("DB_NAME", "visionhub"),

		RedisHost:     getEnv("REDIS_HOST", "localhost"),
		RedisPort:     getEnv("REDIS_PORT", "6379"),
		RedisPassword: os.Getenv("REDIS_PASSWORD"),

		JWTSecret: jwtSecret,

		DeviceUniqueCode:      getEnv("DEVICE_UNIQUE_CODE", "DEVICE_2026_ESP32_K210"),
		DeviceXORKey:          0x4B,
		DeviceActivationToken: os.Getenv("DEVICE_ACTIVATION_TOKEN"),

		OCRServiceURL: getEnv("OCR_SERVICE_URL", ""),

		DoubaoAPIKey: getEnv("DOUBAO_API_KEY", ""),
		DoubaoAPIURL: getEnv("DOUBAO_API_URL", "https://ark.cn-beijing.volces.com"),
		PublicBaseURL: getEnv("PUBLIC_BASE_URL", "http://localhost:3000"),
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
