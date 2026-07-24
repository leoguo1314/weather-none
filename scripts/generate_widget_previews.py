#!/usr/bin/env python3
"""Generate widget preview PNG images for 4x2 widgets."""
from PIL import Image, ImageDraw, ImageFont

def create_gradient(width, height):
    """Create a blue gradient background."""
    img = Image.new('RGBA', (width, height))
    draw = ImageDraw.Draw(img)
    
    # Gradient colors (matching widget_preview_bg.xml)
    start_color = (42, 117, 179)   # #FF2A75B3
    center_color = (76, 155, 220)  # #FF4C9BDC
    end_color = (142, 205, 249)    # #FF8ECDF9
    
    for y in range(height):
        ratio = y / height
        if ratio < 0.5:
            r = int(start_color[0] + (center_color[0] - start_color[0]) * ratio * 2)
            g = int(start_color[1] + (center_color[1] - start_color[1]) * ratio * 2)
            b = int(start_color[2] + (center_color[2] - start_color[2]) * ratio * 2)
        else:
            r = int(center_color[0] + (end_color[0] - center_color[0]) * (ratio - 0.5) * 2)
            g = int(center_color[1] + (end_color[1] - center_color[1]) * (ratio - 0.5) * 2)
            b = int(center_color[2] + (end_color[2] - center_color[2]) * (ratio - 0.5) * 2)
        draw.line([(0, y), (width, y)], fill=(r, g, b, 255))
    
    return img

def create_transparent(width, height):
    """Create a transparent background."""
    return Image.new('RGBA', (width, height), (0, 0, 0, 0))

def draw_rounded_rect(draw, xy, radius, fill):
    """Draw a rounded rectangle."""
    x1, y1, x2, y2 = xy
    draw.rectangle([x1 + radius, y1, x2 - radius, y2], fill=fill)
    draw.rectangle([x1, y1 + radius, x2, y2 - radius], fill=fill)
    draw.pieslice([x1, y1, x1 + 2*radius, y1 + 2*radius], 180, 270, fill=fill)
    draw.pieslice([x2 - 2*radius, y1, x2, y1 + 2*radius], 270, 360, fill=fill)
    draw.pieslice([x1, y2 - 2*radius, x1 + 2*radius, y2], 90, 180, fill=fill)
    draw.pieslice([x2 - 2*radius, y2 - 2*radius, x2, y2], 0, 90, fill=fill)

def create_medium_4x2_preview():
    """Create a medium 4x2 widget preview image (with gradient background)."""
    width, height = 360, 180
    
    # 先创建渐变背景
    img = create_gradient(width, height)
    draw = ImageDraw.Draw(img)
    
    # 圆角裁剪（模拟圆角效果）
    mask = Image.new('L', (width, height), 0)
    mask_draw = ImageDraw.Draw(mask)
    draw_rounded_rect(mask_draw, [0, 0, width-1, height-1], 18, 255)
    
    # 应用圆角遮罩
    result = Image.new('RGBA', (width, height), (0, 0, 0, 0))
    result.paste(img, mask=mask)
    draw = ImageDraw.Draw(result)
    
    try:
        font_large = ImageFont.truetype("arial.ttf", 36)
        font_medium = ImageFont.truetype("arial.ttf", 12)
        font_small = ImageFont.truetype("arial.ttf", 11)
    except:
        font_large = ImageFont.load_default()
        font_medium = ImageFont.load_default()
        font_small = ImageFont.load_default()
    
    white = (255, 255, 255, 255)
    light_white = (255, 255, 255, 200)
    
    # City with location icon
    draw.text((30, 20), "📍 北京市", fill=light_white, font=font_medium)
    
    # Temperature
    draw.text((30, 40), "3°", fill=white, font=font_large)
    
    # Weather params (wind, humidity, AQI, UV)
    draw.text((150, 45), "南风 2级", fill=light_white, font=font_medium)
    draw.text((150, 65), "湿度 45%", fill=light_white, font=font_medium)
    draw.text((240, 45), "空气 良", fill=light_white, font=font_medium)
    draw.text((240, 65), "紫外线 弱", fill=light_white, font=font_medium)
    
    # Weather icon
    sun_x, sun_y = 320, 40
    draw.ellipse([sun_x-18, sun_y-18, sun_x+18, sun_y+18], fill=(255, 220, 100, 255))
    draw.text((300, 65), "晴", fill=light_white, font=font_medium)
    
    # 5-day forecast at bottom
    days = ["今天", "周五", "周六", "周日", "周一"]
    temps = ["-5° 3°", "-3° 5°", "-2° 6°", "0° 7°", "1° 8°"]
    
    for i, (day, temp) in enumerate(zip(days, temps)):
        x = 25 + i * 65
        y = 120
        
        # Small icon
        draw.ellipse([x-6, y-6, x+6, y+6], fill=(255, 220, 100, 255))
        
        # Day name
        draw.text((x + 10, y - 8), day, fill=(255, 255, 255, 180), font=font_small)
        
        # Temp
        draw.text((x + 10, y + 5), temp, fill=white, font=font_small)
    
    return result

def create_new_4x2_preview():
    """Create a new 4x2 widget preview image (transparent background)."""
    width, height = 360, 180
    img = create_transparent(width, height)
    draw = ImageDraw.Draw(img)
    
    try:
        font_large = ImageFont.truetype("arial.ttf", 48)
        font_medium = ImageFont.truetype("arial.ttf", 16)
        font_small = ImageFont.truetype("arial.ttf", 14)
    except:
        font_large = ImageFont.load_default()
        font_medium = ImageFont.load_default()
        font_small = ImageFont.load_default()
    
    white = (255, 255, 255, 255)
    light_white = (255, 255, 255, 200)
    
    # Left side: Clock, Date, City
    # Clock
    draw.text((20, 20), "14:30", fill=white, font=font_large)
    
    # Date
    draw.text((20, 75), "7月24日 星期四", fill=light_white, font=font_medium)
    
    # City
    draw.text((20, 95), "北京市", fill=(255, 255, 255, 180), font=font_small)
    
    # Right side: Weather icon, description, temp
    # Sun icon (larger)
    sun_x, sun_y = 280, 45
    draw.ellipse([sun_x-25, sun_y-25, sun_x+25, sun_y+25], fill=(255, 220, 100, 255))
    
    # Weather description
    draw.text((255, 80), "晴", fill=light_white, font=font_medium)
    
    # Temp range
    draw.text((240, 100), "-5° 3°", fill=(255, 255, 255, 180), font=font_small)
    
    # Bottom: 3-day forecast
    days = [("今天", "-5° 3°"), ("周五", "-3° 5°"), ("周六", "-2° 6°")]
    for i, (day, temp) in enumerate(days):
        x = 40 + i * 110
        y = 145
        
        # Small sun icon
        draw.ellipse([x-10, y-10, x+10, y+10], fill=(255, 220, 100, 255))
        
        # Day name
        draw.text((x + 15, y - 10), day, fill=light_white, font=font_small)
        
        # Temp
        draw.text((x + 15, y + 5), temp, fill=white, font=font_small)
    
    return img

if __name__ == "__main__":
    output_dir = "C:/Users/ttt/weather-none/app/src/main/res/drawable-nodpi"
    
    # Generate medium 4x2 preview (with gradient background)
    img1 = create_medium_4x2_preview()
    img1.save(f"{output_dir}/widget_medium_preview_image.png")
    print(f"Created widget_medium_preview_image.png (with gradient background)")
    
    # Generate new 4x2 preview (transparent background)
    img2 = create_new_4x2_preview()
    img2.save(f"{output_dir}/widget_4x2_preview_image.png")
    print(f"Created widget_4x2_preview_image.png (transparent background)")
    
    print("Done!")