'''
障碍物方位检测(QVGA) + VGA拍照Base64传输
发送：$LEFT! / $MID! / $RIGHT!
接收：$TAKE_PHOTO! → VGA拍照并Base64分块发送
'''
import sensor, image, time, lcd
from maix import KPU
import gc
from fpioa_manager import fm
from machine import UART
import ubinascii

# ==================== 初始化摄像头(QVGA用于检测) ====================
sensor.reset()
sensor.set_pixformat(sensor.RGB565)
sensor.set_framesize(sensor.QVGA)  # 320x240 用于障碍物检测
sensor.set_hmirror(False)
sensor.skip_frames(time=1000)

# ==================== LCD初始化 ====================
lcd.init()
lcd.clear(lcd.BLACK)
lcd.draw_string(10, 10, "Starting...", lcd.WHITE, lcd.BLACK)

# ==================== UART2 初始化 ====================
fm.register(6, fm.fpioa.UART2_RX)
fm.register(8, fm.fpioa.UART2_TX)
uart2 = UART(UART.UART2, 115200, 8, 0, 0, timeout=0, read_buf_len=4096)

lcd.draw_string(10, 30, "UART OK", lcd.WHITE, lcd.BLACK)

# ==================== 模型加载 ====================
od_img = image.Image(size=(320, 256), copy_to_fb=False)
anchor = (0.156250, 0.222548, 0.361328, 0.489583, 0.781250,
          0.983133, 1.621094, 1.964286, 3.574219, 3.94000)
kpu = KPU()
kpu.load_kmodel("/sd/detect_5.kmodel")
kpu.init_yolo2(anchor, 5, 320, 240, 320, 256, 10, 8, 0.7, 0.4, 1)

lcd.draw_string(10, 50, "Model OK", lcd.WHITE, lcd.BLACK)

# 清空启动垃圾
time.sleep_ms(2000)
while uart2.any():
    uart2.read()

lcd.clear(lcd.BLACK)

# ==================== 变量 ====================
clock = time.clock()
uart_buffer = b""

# 图片发送
need_send = False
b64_data = ""
b64_total = 0
photo_count = 0

# 方向
last_direction = ""

# ==================== 辅助函数 ====================
def get_max_box(boxes):
    max_a = 0
    tg = None
    for b in boxes:
        a = b[2] * b[3]
        if a > max_a:
            max_a = a
            tg = b
    return tg

def check_side(box):
    cx = box[0] + box[2] // 2
    if cx < 107:
        return "$LEFT!"
    elif cx < 213:
        return "$MID!"
    else:
        return "$RIGHT!"

def take_vga_photo():
    """切到VGA拍照，然后切回QVGA"""
    sensor.set_framesize(sensor.VGA)      # 640x480
    time.sleep_ms(300)
    img = sensor.snapshot()
    jpg = img.compressed(quality=90)
    sensor.set_framesize(sensor.QVGA)     # 切回320x240
    time.sleep_ms(300)
    return jpg

# ==================== 主循环 ====================
while True:
    clock.tick()

    # 1. 接收指令
    if uart2.any():
        data = uart2.read()
        if data and len(data) > 0:
            uart_buffer += data
            if b"$TAKE_PHOTO!" in uart_buffer:
                uart_buffer = b""
                photo_count += 1

                # VGA拍照 + Base64编码
                jpg = take_vga_photo()
                b64_data = ubinascii.b2a_base64(jpg).decode().strip()
                b64_total = len(b64_data)
                need_send = True

            if len(uart_buffer) > 4096:
                uart_buffer = b""

    # 2. 发送Base64图片
    if need_send:
        uart2.write("$IMG_START:%d!\r\n" % b64_total)
        time.sleep_ms(30)

        for i in range(0, b64_total, 256):
            uart2.write(b64_data[i:i+256] + "\r\n")
            time.sleep_ms(10)

        uart2.write("$IMG_END!\r\n")
        need_send = False
        b64_data = ""

    # 3. 障碍物检测(QVGA输入)
    img = sensor.snapshot()
    od_img.draw_image(img, 0, 0)
    od_img.pix_to_ai()
    kpu.run_with_output(od_img)
    boxes = kpu.regionlayer_yolo2()

    # 4. 发送障碍物方位
    if len(boxes) > 0:
        b = get_max_box(boxes)
        if b:
            direction = check_side(b)
            if direction != last_direction and not need_send:
                uart2.write(direction + "\r\n")
                last_direction = direction
            img.draw_rectangle(b[0], b[1], b[2], b[3], (0, 255, 0), 2)
            img.draw_string(b[0], b[1] - 20, direction, (0, 255, 0))

    # 5. 显示
    img.draw_string(0, 0, "%.1ffps" % clock.fps(), (255, 255, 255))
    if need_send:
        img.draw_string(0, 20, "SEND VGA...", (255, 255, 0))
    elif last_direction:
        img.draw_string(0, 20, last_direction, (0, 255, 0))
    img.draw_string(0, 40, "Photo:%d" % photo_count, (200, 200, 200))

    lcd.display(img)
    gc.collect()
    time.sleep_ms(10)