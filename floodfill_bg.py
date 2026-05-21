from PIL import Image

def remove_background_floodfill(input_path, output_path, tolerance=50):
    # Open image
    img = Image.open(input_path).convert("RGBA")
    width, height = img.size
    
    # We will do a BFS flood fill starting from (0,0)
    # The checkerboard background might have variations, so we check if the pixel is "light colored" 
    # (i.e. part of the white/grey checkerboard)
    
    data = img.load()
    visited = set()
    queue = [(0, 0)]
    
    # Also add other corners just in case (0,0) is not background
    queue.extend([(width-1, 0), (0, height-1), (width-1, height-1)])
    
    while queue:
        x, y = queue.pop(0)
        
        if (x, y) in visited:
            continue
        if x < 0 or x >= width or y < 0 or y >= height:
            continue
            
        visited.add((x, y))
        
        r, g, b, a = data[x, y]
        
        # Checkerboard is usually white and light grey. 
        # Check if color is relatively light/greyish.
        # Most checkerboards are > 180 in RGB and have low saturation.
        # Alternatively, since the flag is blue/gold and the pole is brown, 
        # any pixel that is very light (R>150, G>150, B>150) can be considered background.
        
        if r > 150 and g > 150 and b > 150:
            # Make it transparent
            data[x, y] = (255, 255, 255, 0)
            
            # Add neighbors
            queue.append((x+1, y))
            queue.append((x-1, y))
            queue.append((x, y+1))
            queue.append((x, y-1))

    img.save(output_path, "PNG")

remove_background_floodfill(
    "/Users/kirinokazuya/.gemini/antigravity-ide/brain/02626b54-7c63-4286-bc50-9b6f0807eb6a/territory_flag_1779277698913.png",
    "/Users/kirinokazuya/Documents/ソースコード類/territory-conquest-mod/src/main/resources/assets/territory_conquest/textures/block/territory_flag.png"
)
