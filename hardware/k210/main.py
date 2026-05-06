import sensor, image, time
from maix import KPU
import gc
from fpioa_manager import fm
from machine import UART
import ubinascii

sensor.reset()
sensor.set_pixformat(sensor.RGB565)
sensor.set_framesize(sensor.QVGA)
sensor.set_hmirror(False)
sensor.skip_frames(time=1000)
fm.register(6, fm.fpioa.UART2_RX)
fm.register(8, fm.fpioa.UART2_TX)
uart2 = UART(UART.UART2, 115200, 8, 0, 0, timeout=0, read_buf_len=4096)
od_img = image.Image(size=(320, 256), copy_to_fb=False)
anchor = (0.156250, 0.222548, 0.361328, 0.489583, 0.781250,
          0.983133, 1.621094, 1.964286, 3.574219, 3.94000)
kpu = KPU()
kpu.load_kmodel("/sd/detect.kmodel")
kpu.init_yolo2(anchor, 5, 320, 240, 320, 256, 10, 8, 0.7, 0.4, 1)
time.sleep_ms(2000)
while uart2.any():
    uart2.read()
uart_buffer = b""
need_send = False
b64_data = ""
b64_total = 0
last_direction = ""
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
    sensor.set_framesize(sensor.VGA)
    time.sleep_ms(300)
    img = sensor.snapshot()
    jpg = img.compressed(quality=90)
    sensor.set_framesize(sensor.QVGA)
    time.sleep_ms(300)
    return jpg

while True:
    if uart2.any():
        data = uart2.read()
        if data and len(data) > 0:
            uart_buffer += data
            if b"$TAKE_PHOTO!" in uart_buffer:
                uart_buffer = b""
                jpg = take_vga_photo()
                b64_data = ubinascii.b2a_base64(jpg).decode().strip()
                b64_total = len(b64_data)
                need_send = True
            if len(uart_buffer) > 4096:
                uart_buffer = b""
    if need_send:
        uart2.write("$IMG_START:%d!\r\n" % b64_total)
        time.sleep_ms(30)
        for i in range(0, b64_total, 256):
            uart2.write(b64_data[i:i+256] + "\r\n")
            time.sleep_ms(10)
        uart2.write("$IMG_END!\r\n")
        need_send = False
        b64_data = ""
    img = sensor.snapshot()
    od_img.draw_image(img, 0, 0)
    od_img.pix_to_ai()
    kpu.run_with_output(od_img)
    boxes = kpu.regionlayer_yolo2()
    if len(boxes) > 0:
        b = get_max_box(boxes)
        if b:
            direction = check_side(b)
            if direction != last_direction and not need_send:
                uart2.write(direction + "\r\n")
                last_direction = direction
    gc.collect()
    time.sleep_ms(10)
