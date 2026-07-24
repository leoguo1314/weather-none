from PIL import Image, ImageDraw, ImageFont
import os

def create_2x2_preview():
    # Create a 360x360 image with gradient background
    width, height = 360, 360
    img = Image.new('RGBA', (width, height))
    draw = ImageDraw.Draw(img)
    
    # Draw gradient background (sunny day gradient)
    for y in range(height):
        r = int(31 + (101 - 31) * y / height)  # #1F5F9C to #65B3EA
        g = int(95 + (179 - 95) * y / height)
        b = int(156 + (234 - 156) * y / height)
        draw.line([(0, y), (width, y)], fill=(r, g, b, 255))
    
    # Try to use a font, fallback to default if not available
    try:
        font_large = ImageFont.truetype("arial.ttf", 48)
        font_medium = ImageFont.truetype("arial.ttf", 24)
        font_small = ImageFont.truetype("arial.ttf", 16)
        font_tiny = ImageFont.truetype("arial.ttf", 14)
    except:
        font_large = ImageFont.load_default()
        font_medium = ImageFont.load_default()
        font_small = ImageFont.load_default()
        font_tiny = ImageFont.load_default()
    
    # Draw temperature (top left)
    draw.text((20, 30), "26°", fill="white", font=font_large)
    
    # Draw sun icon placeholder (top right)
    draw.ellipse([280, 30, 330, 80], fill="yellow", outline="orange", width=2)
    
    # Draw city name (below sun icon)
    draw.text((280, 85), "当前位置", fill="#EEFFFFFF", font=font_small)
    
    # Draw forecast row (bottom)
    forecast_y = height - 100
    
    # Now
    draw.text((30, forecast_y), "现在", fill="#BBFFFFFF", font=font_tiny)
    draw.ellipse([30, forecast_y + 20, 70, forecast_y + 60], fill="yellow", outline="orange", width=2)
    draw.text((35, forecast_y + 65), "26°", fill="white", font=font_small)
    
    # +1h
    draw.text((150, forecast_y), "15:00", fill="#BBFFFFFF", font=font_tiny)
    draw.ellipse([150, forecast_y + 20, 190, forecast_y + 60], fill="yellow", outline="orange", width=2)
    draw.text((155, forecast_y + 65), "27°", fill="white", font=font_small)
    
    # +2h
    draw.text((270, forecast_y), "16:00", fill="#BBFFFFFF", font=font_tiny)
    draw.ellipse([270, forecast_y + 20, 310, forecast_y + 60], fill="yellow", outline="orange", width=2)
    draw.text((275, forecast_y + 65), "25°", fill="white", font=font_small)
    
    # Save the image
    output_path = "C:/Users/linsh/weather-none/app/src/main/res/drawable-nodpi/widget_preview_image.png"
    img.save(output_path, "PNG")
    print(f"Generated 2x2 preview image: {output_path}")

if __name__ == "__main__":
    create_2x2_preview()
