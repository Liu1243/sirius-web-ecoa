from PIL import Image, ImageDraw, ImageFont
import os

colors = {
    'Step0': ('#ECEFF1', '#263238', '0'),
    'Step1': ('#E3F2FD', '#0D47A1', '1'),
    'Step2': ('#E8F5E9', '#1B5E20', '2'),
    'Step3': ('#FFF3E0', '#E65100', '3'),
    'Step4': ('#F3E5F5', '#4A148C', '4'),
    'Step5': ('#FFEBEE', '#B71C1C', '5'),
    'Steps': ('#E0F2F1', '#00897B', 'S')
}

out_dir = 'c:/Users/1/code/sirius-web-ecoa/packages/edt/backend/sirius-components-edt-edit/icons/full/obj16'

for name, (bg, fg, text) in colors.items():
    img = Image.new('RGB', (16, 16), color=bg)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=fg)
    d.text((5, 2), text, fill=fg)
    img.save(os.path.join(out_dir, f'{name}.gif'))
