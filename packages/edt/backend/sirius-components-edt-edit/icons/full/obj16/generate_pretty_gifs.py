from PIL import Image, ImageDraw, ImageFont
import os

colors = {
    'Step0': ('#F5F5F5', '#424242', '#BDBDBD', '0'),
    'Step1': ('#E3F2FD', '#0D47A1', '#64B5F6', '1'),
    'Step2': ('#E8F5E9', '#1B5E20', '#81C784', '2'),
    'Step3': ('#FFF8E1', '#E65100', '#FFCA28', '3'),
    'Step4': ('#F3E5F5', '#4A148C', '#CE93D8', '4'),
    'Step5': ('#FFEBEE', '#B71C1C', '#EF9A9A', '5'),
    'Steps': ('#E0F2F1', '#004D40', '#80CBC4', 'S')
}

out_dir = r"c:\Users\1\code\sirius-web-ecoa\packages\edt\backend\sirius-components-edt-edit\icons\full\obj16"

SCALE = 8
size = 16 * SCALE

try:
    font = ImageFont.truetype("arialbd.ttf", 10 * SCALE)
except IOError:
    try:
        font = ImageFont.truetype("segoeuib.ttf", 10 * SCALE)
    except IOError:
        font = ImageFont.load_default()

for name, (bg, fg, border, text) in colors.items():
    # Base image with white background
    img = Image.new('RGB', (size, size), color='#FFFFFF')
    draw = ImageDraw.Draw(img)
    
    padding = 1.5 * SCALE
    box = [padding, padding, size - padding, size - padding]
    radius = 3.5 * SCALE
    
    draw.rounded_rectangle(box, radius=radius, fill=bg, outline=border, width=int(1.5 * SCALE))
    
    cx = size / 2
    cy = size / 2
    
    # Draw text perfectly centered
    try:
        draw.text((cx, cy - 0.5 * SCALE), text, fill=fg, font=font, anchor="mm")
    except Exception:
        # Fallback for old PIL
        bbox = draw.textbbox((0, 0), text, font=font)
        tw = bbox[2] - bbox[0]
        th = bbox[3] - bbox[1]
        draw.text((cx - tw/2, cy - th/2 - 2*SCALE), text, fill=fg, font=font)
    
    final_img = img.resize((16, 16), resample=Image.Resampling.LANCZOS)
    gif_img = final_img.convert('P', palette=Image.Palette.ADAPTIVE, colors=256)
    
    path = os.path.join(out_dir, f'{name}.gif')
    gif_img.save(path)
    print(f"Saved {path}")
