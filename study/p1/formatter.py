

"""
接受文本,返回格式化后的文本
"""
def format_idea(line):
    """
    格式化文本
    :param line: 输入的文本
    :return: 格式化后的文本
    """
    # 去除首尾空格
    cleaned_line = line.strip()
    return f"灵感: {cleaned_line}"



def main():

    with open("ideas.txt", "r", encoding="utf-8") as file:
        lines = file.readlines()

    with open("formatted_ideas.txt", "w", encoding="utf-8") as f:
        for line in lines:
            if not line.strip():
                continue
            f.write(format_idea(line) + "\n")

if __name__ == "__main__":
    main()
