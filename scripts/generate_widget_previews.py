#!/usr/bin/env python3
"""Generate widget preview PNG images for 4x2 widgets."""
from PIL import Image, ImageDraw, ImageFont
import os

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

def draw_rounded_rect(draw, xy, radius, fill):
    """Draw a rounded rectangle."""
    x1, y1, x2, y2 = xy
    draw.rectangle([x1 + radius, y1, x2 - radius, y2], fill=fill)
    draw.rectangle([x1, y1 + radius, x2, y2 - radius], fill=fill)
    draw.pieslice([x1, y1, x1 + 2*radius, y1 + 2*radius], 180, 270, fill=fill)
    draw.pieslice([x2 - 2*radius, y1, x2, y1 + 2*radius], 270, 360, fill=fill)
    draw.pieslice([x1, y2 - 2*radius, x1 + 2*radius, y2], 90, 180, fill=fill)
    draw.pieslice([x2 - 2*radius, y2 - 2*radius, x2, y2], 0, 90, fill=fill)

def create_4x2_preview():
    """Create a 4x2 widget preview image."""
    width, height = 360, 180
    img = create_gradient(width, height)
    draw = ImageDraw.Draw(img)
    
    # Semi-transparent white card
    card_color = (255, 255, 255, 50)
    draw_rounded_rect(draw, [10, 10, width-10, height-10], 18, card_color)
    
    # Try to use a system font
    try:
        font_large = ImageFont.truetype("arial.ttf", 36)
        font_medium = ImageFont.truetype("arial.ttf", 14)
        font_small = ImageFont.truetype("arial.ttf", 12)
    except:
        font_large = ImageFont.load_default()
        font_medium = ImageFont.load_default()
        font_small = ImageFont.load_default()
    
    # Left side: Clock, Date, City
    white = (255, 255, 255, 255)
    light_white = (255, 255, 255, 200)
    
    # Clock
    draw.text((25, 30), "14:30", fill=white, font=font_large)
    
    # Date
    draw.text((25, 75), "7月24日 星期四", fill=light_white, font=font_medium)
    
    # City
    draw.text((25, 95), "北京市", fill=(255, 255, 255, 180), font=font_small)
    
    # Right side: Weather icon placeholder, description, temp
    # Sun icon (circle)
    sun_x, sun_y = 270, 40
    draw.ellipse([sun_x-20, sun_y-20, sun_x+20, sun_y+20], fill=(255, 220, 100, 255))
    
    # Weather description
    draw.text((245, 75), "晴", fill=light_white, font=font_medium)
    
    # Temp range
    draw.text((230, 95), "-5° 3°", fill=(255, 255, 255, 180), font=font_small)
    
    # Bottom: 3-day forecast
    for i, (day, temp) in enumerate([("今天", "-5° 3°"), ("周五", "-3° 5°"), ("周六", "-2° 6°")]):
        x = 40 + i * 110
        y = 130
        
        # Small sun icon
        draw.ellipse([x-8, y-8, x+8, y+8], fill=(255, 220, 100, 255))
        
        # Day name
        draw.text((x + 15, y - 10), day, fill=(255, 255, 255, 200), font=font_small)
        
        # Temp
        draw.text((x + 15, y + 5), temp, fill=white, font=font_small)
    
    return img

def create_medium_4x2_preview():
    """Create a medium 4x2 widget preview image."""
    width, height = 360, 180
    img = create_gradient(width, height)
    draw = ImageDraw.Draw(img)
    
    # Semi-transparent white card
    card_color = (255, 255, 255, 50)
    draw_rounded_rect(draw, [10, 10, width-10, height-10], 18, card_color)
    
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
    
    return img

if __name__ == "__main__":
    output_dir = "C:/Users/ttt/weather-none/app/src/main/res/drawable-nodpi"
    
    # Generate new 4x2 preview
    img1 = create_4x2_preview()
    img1.save(os.path.join(output_dir, "widget_4x2_preview_image.png"))
    print(f"Created widget_4x2_preview_image.png")
    
    # Generate medium 4x2 preview
    img2 = create_medium_4x2_preview()
    img2.save(os.path.join(output_dir, "widget_medium_preview_image.png"))
    print(f"Created widget_medium_preview_image.png")
    
    print("Done!")