from PIL import Image

def make_transparent(input_path, output_path, tolerance=40):
    img = Image.open(input_path).convert("RGBA")
    data = img.getdata()
    
    new_data = []
    # Assume background is white or light grey.
    for item in data:
        if item[0] > 255 - tolerance and item[1] > 255 - tolerance and item[2] > 255 - tolerance:
            new_data.append((255, 255, 255, 0)) # Transparent
        else:
            new_data.append(item)
            
    img.putdata(new_data)
    img.save(output_path, "PNG")

make_transparent(
    "/Users/kirinokazuya/.gemini/antigravity-ide/brain/02626b54-7c63-4286-bc50-9b6f0807eb6a/territory_flag_1779277698913.png",
    "/Users/kirinokazuya/Documents/ソースコード類/territory-conquest-mod/src/main/resources/assets/territory_conquest/textures/block/territory_flag.png"
)
