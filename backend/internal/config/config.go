package config

import "os"

type Config struct {
	ServerPort string

	DBHost string
	DBPort string
	DBUser string
	DBPass string
	DBName string

	RedisHost string
	RedisPort string

	JWTSecret string

	DeviceUniqueCode string
	DeviceXORKey     byte

	OCRServiceURL string

	DoubaoAPIKey string
	DoubaoAPIURL string
}

func Load() *Config {
	return &Config{
		ServerPort: getEnv("SERVER_PORT", "8888"),

		DBHost: getEnv("DB_HOST", "localhost"),
		DBPort: getEnv("DB_PORT", "5432"),
		DBUser: getEnv("DB_USER", "visionhub"),
		DBPass: getEnv("DB_PASSWORD", "visionhub"),
		DBName: getEnv("DB_NAME", "visionhub"),

		RedisHost: getEnv("REDIS_HOST", "localhost"),
		RedisPort: getEnv("REDIS_PORT", "6379"),

		JWTSecret: getEnv("JWT_SECRET", ""),

		DeviceUniqueCode: getEnv("DEVICE_UNIQUE_CODE", "DEVICE_2026_ESP32_K210"),
		DeviceXORKey:     0x4B,

		OCRServiceURL: getEnv("OCR_SERVICE_URL", ""),

		DoubaoAPIKey: getEnv("DOUBAO_API_KEY", ""),
		DoubaoAPIURL: getEnv("DOUBAO_API_URL", "https://ark.cn-beijing.volces.com"),
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
