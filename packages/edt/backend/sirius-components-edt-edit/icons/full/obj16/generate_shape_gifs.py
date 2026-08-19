from PIL import Image, ImageDraw
import os

out_dir = r"c:\Users\1\code\sirius-web-ecoa\packages\edt\backend\sirius-components-edt-edit\icons\full\obj16"
SCALE = 8
size = 16 * SCALE

def create_base(bg, border):
    img = Image.new('RGB', (size, size), color='#FFFFFF')
    draw = ImageDraw.Draw(img)
    padding = 1.5 * SCALE
    box = [padding, padding, size - padding, size - padding]
    radius = 3.5 * SCALE
    draw.rounded_rectangle(box, radius=radius, fill=bg, outline=border, width=int(1.5 * SCALE))
    return img, draw

def save_image(img, name):
    final_img = img.resize((16, 16), resample=Image.Resampling.LANCZOS)
    gif_img = final_img.convert('P', palette=Image.Palette.ADAPTIVE, colors=256)
    path = os.path.join(out_dir, f'{name}.gif')
    gif_img.save(path)
    print(f"Saved {path}")

# Step0: Types - Triangle, Circle, Square
img0, d0 = create_base('#F5F5F5', '#BDBDBD')
fg0 = '#424242'
d0.polygon([(size/2, 4*SCALE), (11*SCALE, 8.5*SCALE), (5*SCALE, 8.5*SCALE)], fill=fg0)
d0.ellipse([4*SCALE, 9.5*SCALE, 7.5*SCALE, 13*SCALE], fill=fg0)
d0.rectangle([8.5*SCALE, 9.5*SCALE, 12*SCALE, 13*SCALE], fill=fg0)
save_image(img0, 'Step0')

# Step1: Services - Two linked nodes (dumbbell)
img1, d1 = create_base('#E3F2FD', '#64B5F6')
fg1 = '#0D47A1'
# Line connecting the two
d1.line([(6*SCALE, 6*SCALE), (10*SCALE, 10*SCALE)], fill=fg1, width=int(2*SCALE))
# Two nodes
d1.ellipse([4.5*SCALE, 4.5*SCALE, 7.5*SCALE, 7.5*SCALE], fill=fg1)
d1.ellipse([8.5*SCALE, 8.5*SCALE, 11.5*SCALE, 11.5*SCALE], fill=fg1)
save_image(img1, 'Step1')

# Step2: Component Definitions - 2x2 Grid of small blocks
img2, d2 = create_base('#E8F5E9', '#81C784')
fg2 = '#1B5E20'
bs = 3 * SCALE
spacing = 1.5 * SCALE
start_x = (size - 2*bs - spacing)/2
start_y = (size - 2*bs - spacing)/2

d2.rounded_rectangle([start_x, start_y, start_x+bs, start_y+bs], radius=0.5*SCALE, fill=fg2)
d2.rounded_rectangle([start_x+bs+spacing, start_y, start_x+2*bs+spacing, start_y+bs], radius=0.5*SCALE, fill=fg2)
d2.rounded_rectangle([start_x, start_y+bs+spacing, start_x+bs, start_y+2*bs+spacing], radius=0.5*SCALE, fill=fg2)
d2.rounded_rectangle([start_x+bs+spacing, start_y+bs+spacing, start_x+2*bs+spacing, start_y+2*bs+spacing], radius=0.5*SCALE, fill=fg2)
save_image(img2, 'Step2')

# Step3: Initial Assembly - Arrow linking two blocks
img3, d3 = create_base('#FFF8E1', '#FFCA28')
fg3 = '#E65100'
d3.rectangle([4*SCALE, 5*SCALE, 6.5*SCALE, 11*SCALE], fill=fg3)
d3.line([(6.5*SCALE, 8*SCALE), (11*SCALE, 8*SCALE)], fill=fg3, width=int(1.5*SCALE))
d3.polygon([(9.5*SCALE, 6.5*SCALE), (12*SCALE, 8*SCALE), (9.5*SCALE, 9.5*SCALE)], fill=fg3)
save_image(img3, 'Step3')

# Step4: Component Implementations - Code lines in a box
img4, d4 = create_base('#F3E5F5', '#CE93D8')
fg4 = '#4A148C'
d4.line([(5.5*SCALE, 6*SCALE), (10.5*SCALE, 6*SCALE)], fill=fg4, width=int(1.5*SCALE))
d4.line([(5.5*SCALE, 8.5*SCALE), (8.5*SCALE, 8.5*SCALE)], fill=fg4, width=int(1.5*SCALE))
d4.line([(5.5*SCALE, 11*SCALE), (10.5*SCALE, 11*SCALE)], fill=fg4, width=int(1.5*SCALE))
d4.rounded_rectangle([4*SCALE, 4*SCALE, 12*SCALE, 12*SCALE], radius=0.5*SCALE, outline=fg4, width=int(1*SCALE))
save_image(img4, 'Step4')

# Step5: Deployments - Stack of 2 servers
img5, d5 = create_base('#FFEBEE', '#EF9A9A')
fg5 = '#B71C1C'
d5.rounded_rectangle([4.5*SCALE, 4.5*SCALE, 11.5*SCALE, 7.5*SCALE], radius=1*SCALE, fill=fg5)
d5.rounded_rectangle([4.5*SCALE, 8.5*SCALE, 11.5*SCALE, 11.5*SCALE], radius=1*SCALE, fill=fg5)
d5.ellipse([5.5*SCALE, 5.5*SCALE, 6.5*SCALE, 6.5*SCALE], fill='#FFFFFF')
d5.ellipse([5.5*SCALE, 9.5*SCALE, 6.5*SCALE, 10.5*SCALE], fill='#FFFFFF')
save_image(img5, 'Step5')

# Steps: Staircase
imgS, dS = create_base('#E0F2F1', '#80CBC4')
fgS = '#004D40'
dS.polygon([
    (4*SCALE, 12*SCALE), 
    (12*SCALE, 12*SCALE), 
    (12*SCALE, 4*SCALE),
    (9.5*SCALE, 4*SCALE),
    (9.5*SCALE, 6.5*SCALE),
    (7*SCALE, 6.5*SCALE),
    (7*SCALE, 9*SCALE),
    (4*SCALE, 9*SCALE)
], fill=fgS)
save_image(imgS, 'Steps')
